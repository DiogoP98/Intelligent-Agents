package group13;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.linear.LinearConstraint;
import org.apache.commons.math3.optimization.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optimization.linear.Relationship;
import org.apache.commons.math3.optimization.linear.SimplexSolver;

import java.util.*;

@SuppressWarnings("deprecation")

public class UncertaintyModelling extends AdditiveUtilitySpaceFactory {
    private final Domain domain;
    private AdditiveUtilitySpace u;
    private ArrayList<Integer>[][] positions;
    private Double[][] means;
    private int threshold;
    //Maps each issue to an integer in range [0,i-1]; i - number of issues
    private HashMap<Integer, Integer> mapping_issues = new HashMap<>();
    //Maps the number of values per issue
    private HashMap<Integer, Integer> number_values_per_issue = new HashMap<>();
    //Maps each value to an integer in range [0,v-1]; v - number of values
    private HashMap<String, Integer> mapping_values = new HashMap<String, Integer>();

    /**
     * Generates an simple Utility Space on the domain, with equal weights and zero values.
     * Everything is zero-filled to already have all keys contained in the utility maps.
     *
     * @param d Negotiation Domain
     */
    public UncertaintyModelling(Domain d) {
        super(d);
        this.domain = d;
        this.threshold = 3500;
    }

    /**
     * Setups the data structures for further calculations.
     *
     * @param bids Bid Ranking
     */
    public void UncertaintyEstimation(BidRanking bids) {
        List<Bid> Bids = bids.getBidOrder();
        double lowerUtility = bids.getLowUtility();
        double higherUtility = bids.getHighUtility();

        List<Issue> issues = this.getDomain().getIssues();
        this.positions = new ArrayList[this.getDomain().getIssues().size()][];
        this.means = new Double[this.getDomain().getIssues().size()][];

        int vars = 0;
        int key = 0;
        for (Issue issue : issues) {
            int issueNumber = issue.getNumber();
            IssueDiscrete issueD = (IssueDiscrete) issue;
            int nvalues_issue = issueD.getValues().size();
            this.positions[key] = new ArrayList[nvalues_issue];
            this.means[key] = new Double[nvalues_issue];

            int count_values = 0;
            for (ValueDiscrete valueDiscrete : issueD.getValues()) {
                this.mapping_values.put(valueDiscrete.toString() + issueNumber, count_values);

                this.positions[key][count_values] = new ArrayList<>();

                count_values += 1;
            }

            this.number_values_per_issue.put(key, nvalues_issue);
            this.mapping_issues.put(issueNumber, key++);
            vars += nvalues_issue;
        }

        if (vars * Bids.size() <= this.threshold)
            LP(Bids, vars, lowerUtility, higherUtility, issues);
        else
            heuristic_calculation(Bids, issues);

        for (Issue i : issues) {
            EvaluatorDiscrete evaluator = (EvaluatorDiscrete) this.u.getEvaluator(i);
            evaluator.normalizeAll();
        }
        this.scaleAllValuesFrom0To1();
        this.normalizeWeights();
    }

    /**
     * Calculates uncertainty modelling based on an heuristic, taking into account the position of each value
     * in the ranking bid.
     *
     * @param Bids List of Bids ordered by relative preference
     */
    private void heuristic_calculation(List<Bid> Bids, List<Issue> issues) {
        this.u = getUtilitySpace();

        int points = 1;

        for (Bid b : Bids) {
            for (Issue issue : b.getIssues()) {
                int issueNumber = issue.getNumber();
                ValueDiscrete value = (ValueDiscrete) b.getValue(issueNumber);

                this.positions[mapping_issues.get(issueNumber)][mapping_values.get(value + String.valueOf(issueNumber))].add(points);
            }

            points += 1;
        }

        for (Issue issue : issues) {
            int issueNumber = issue.getNumber();
            IssueDiscrete issueD = (IssueDiscrete) issue;
            int key = mapping_issues.get(issueNumber);

            double max_deviation = 0;

            for (ValueDiscrete valueDiscrete : issueD.getValues()) {
                double total = 0;
                double size = this.positions[key][mapping_values.get(valueDiscrete + String.valueOf(issueNumber))].size();
                List<Integer> value_position = this.positions[key][mapping_values.get(valueDiscrete + String.valueOf(issueNumber))];

                for (Integer pos : value_position)
                    total += pos;

                //get the mean position of the current value on the bid ranking
                double mean = total / size;
                double deviation = 0;
                for (Integer pos : value_position)
                    deviation += (mean - pos) * (mean - pos);

                deviation = deviation / size;
                max_deviation = Math.max(deviation, max_deviation);
                this.setUtility(issue, valueDiscrete, mean);
                EvaluatorDiscrete evaluator = (EvaluatorDiscrete) this.u.getEvaluator(issue);
            }

            this.setWeight(issue, 1 / max_deviation);
        }
    }

    /**
     * Calculates uncertainty modelling based on an linear programming
     *
     * @param Bids          List of Bids ordered by relative preference
     * @param vars          number of values
     * @param lowerUtility  the lowest utility in the ranking
     * @param higherUtility the highest utility in the ranking
     * @param issues        List of issues in the domain
     */
    private void LP(List<Bid> Bids, int vars, double lowerUtility, double higherUtility, List<Issue> issues) {
        this.u = getUtilitySpace();

        //add the number of slack variables
        int slackvars = Bids.size() - 1;
        vars += slackvars;

        double[] variables = new double[vars];

        for (int i = 0; i < slackvars; i++) {
            variables[i] = 1;
        }

        LinearObjectiveFunction objectiveFunction = new LinearObjectiveFunction(variables, 0);

        Collection<LinearConstraint> constraints = new ArrayList<>();
        for (int pos = 0; pos < vars; pos++) {
            Arrays.fill(variables, 0);
            variables[pos] = 1;
            constraints.add(new LinearConstraint(variables, Relationship.GEQ, 0));
        }
        Arrays.fill(variables, 0);

        double[] previous_values = new double[vars];
        int k = 0;

        for (Bid b : Bids) {
            double[] values_issue = get_values_bid(b, slackvars, vars);

            //constraint the lowest value bid to have the same value as the given one
            if (k == 0)
                constraints.add(new LinearConstraint(values_issue, Relationship.EQ, lowerUtility));
            else {
                for (int pos = 0; pos < vars; pos++)
                    variables[pos] = values_issue[pos] - previous_values[pos];

                //add correspondent slack variable
                variables[k - 1] = 1;
                constraints.add(new LinearConstraint(variables, Relationship.GEQ, 0));
            }

            //constraint the biggest value bid to have the same value as the given one
            if (k == slackvars)
                constraints.add(new LinearConstraint(values_issue, Relationship.EQ, higherUtility));

            k += 1;

            for (int pos = 0; pos < vars; pos++)
                previous_values[pos] = values_issue[pos];
        }

        SimplexSolver solver = new SimplexSolver();
        solver.setMaxIterations(Integer.MAX_VALUE);
        PointValuePair solution = solver.optimize(objectiveFunction, constraints, GoalType.MINIMIZE, true);

        int pos = slackvars;
        for (Issue issue : issues) {
            IssueDiscrete issueD = (IssueDiscrete) issue;
            for (ValueDiscrete valueD : issueD.getValues()) {
                this.setUtility(issue, valueD, solution.getPoint()[pos]);
                pos += 1;
            }
        }
    }

    /**
     * Setups the arrays of the current bid, for linear programming
     *
     * @param b            Current bid
     * @param numberSlacks Number of slack variables
     * @param arraySize    Total number of variables
     * @return array representing a constraint for linear programming
     */
    private double[] get_values_bid(Bid b, int numberSlacks, int arraySize) {
        double[] values = new double[arraySize];

        for (Issue issue : b.getIssues()) {
            int issueNumber = issue.getNumber();
            IssueDiscrete issueD = (IssueDiscrete) issue;

            ValueDiscrete v = (ValueDiscrete) b.getValue(issueNumber);

            int index = numberSlacks;
            int key = mapping_issues.get(issueNumber);
            //get the number of issues before this, and respective number of values
            for (int issues = 0; issues < key; issues++)
                index += this.number_values_per_issue.get(issues);

            for (ValueDiscrete valueD : issueD.getValues()) {
                if (valueD.hashCode() == v.hashCode()) {
                    values[index] = 1;
                    break;
                } else
                    index += 1;
            }
        }

        return values;
    }
}
