package group13;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AdditiveUtilitySpace;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.linear.*;
import org.apache.commons.math3.optimization.GoalType;

import java.util.*;

@SuppressWarnings("deprecation")

public class UncertaintyModelling extends AdditiveUtilitySpaceFactory {
    private final Domain domain;
    private int threshold;
    public AdditiveUtilitySpace u;

    private HashMap<Integer, Integer> mapping_issues = new HashMap<>();
    private HashMap<Integer, Integer> number_values_per_issue = new HashMap<>();

    private HashMap<String, Integer> mapping_values = new HashMap<String, Integer>();

    ArrayList<Integer>[][] positions;
    Double [][] means;

    /**
     * Generates an simple Utility Space on the domain, with equal weights and zero values.
     * Everything is zero-filled to already have all keys contained in the utility maps.
     *
     * @param d
     */
    public UncertaintyModelling(Domain d) {
        super(d);
        this.domain = d;
        this.threshold = 6000;
    }

    public void UncertaintyEstimation(BidRanking bids) {
        List<Bid> Bids = bids.getBidOrder();
        double lowerUtility = bids.getLowUtility();
        double higherUtility = bids.getHighUtility();

        List<Issue> issues = this.getDomain().getIssues();
        this.positions = new ArrayList[this.getDomain().getIssues().size()][];
        this.means = new Double[this.getDomain().getIssues().size()][];

        int vars = 0;
        int key = 0;
        for(Issue issue: issues) {
            int issueNumber = issue.getNumber();
            IssueDiscrete issueD = (IssueDiscrete) issue;
            int nvalues_issue = issueD.getValues().size();
            this.positions[key] = new ArrayList[nvalues_issue];
            this.means[key] = new Double[nvalues_issue];

            int count_values = 0;
            for (ValueDiscrete valueDiscrete : issueD.getValues()) {
                this.mapping_values.put(valueDiscrete.toString()+ String.valueOf(issueNumber), count_values);

                this.positions[key][count_values] = new ArrayList<>();

                count_values += 1;
            }

            this.number_values_per_issue.put(key,nvalues_issue);
            this.mapping_issues.put(issueNumber, key++);
            vars += nvalues_issue;
        }

        if(vars * Bids.size() <= this.threshold)
            LP(Bids, vars, lowerUtility, higherUtility, issues);
        else
            heuristic_calculation(Bids);

    }

    private void heuristic_calculation(List<Bid> Bids) {
        int points = 1;

        for(Bid b: Bids) {
            for(Issue issue: b.getIssues()) {
                int issueNumber = issue.getNumber();
                ValueDiscrete value = (ValueDiscrete) b.getValue(issueNumber);

                this.positions[mapping_issues.get(issueNumber)][mapping_values.get(value + String.valueOf(issueNumber))].add(points);
            }

            points += 1;
        }

        for(Issue issue: this.getDomain().getIssues()) {
            int issueNumber = issue.getNumber();
            IssueDiscrete issueD = (IssueDiscrete) issue;
            int key = mapping_issues.get(issueNumber);

            double max_deviation = 0;

            int value_number = 0;
            for (ValueDiscrete valueDiscrete : issueD.getValues()) {
                double total = 0;
                double size = (double) this.positions[key][mapping_values.get(valueDiscrete + String.valueOf(issueNumber))].size();
                List<Integer> value_position = this.positions[key][mapping_values.get(valueDiscrete + String.valueOf(issueNumber))];

                for(Integer pos: value_position)
                    total += pos;

                double mean = total / size;
                double deviation = 0;
                for(Integer pos: value_position)
                    deviation += (mean - pos)*(mean - pos);

                deviation = deviation / size;
                max_deviation = Math.max(deviation, max_deviation);
                this.setUtility(issue, valueDiscrete, mean);
            }

            this.setWeight(issue, 1 / max_deviation);
        }

        this.normalizeWeights();
    }

    private void LP(List<Bid> Bids, int vars, double lowerUtility, double higherUtility, List<Issue> issues) {
        this.u = getUtilitySpace();

        //add the number of slack variables
        int slackvars = Bids.size() - 1;
        vars += slackvars;

        double[] variables = new double[vars];

        for(int i = 0; i < slackvars; i++) {
            variables[i] = 1;
        }

        LinearObjectiveFunction objectiveFunction = new LinearObjectiveFunction(variables, 0);

        Collection<LinearConstraint> constraints = new ArrayList<>();
        for(int pos = 0; pos < vars; pos++) {
            Arrays.fill(variables, 0);
            variables[pos] = 1;
            constraints.add(new LinearConstraint(variables, Relationship.GEQ, 0));
        }
        Arrays.fill(variables, 0);

        double [] previous_values = new double[vars];
        int k = 0;

        for(Bid b: Bids) {
            double [] values_issue = get_values_bid(b, slackvars, vars);

            //constraint the lowest value bid to have the same value as the given one
            if(k == 0)
                constraints.add(new LinearConstraint(values_issue, Relationship.EQ, lowerUtility));
            else {
                for(int pos = 0; pos < vars; pos++)
                    variables[pos] = values_issue[pos] - previous_values[pos];

                //add correspondent slack variable
                variables[k-1] = 1;
                constraints.add(new LinearConstraint(variables, Relationship.GEQ, 0));
            }

            //constraint the biggest value bid to have the same value as the given one
            if(k == slackvars)
                constraints.add(new LinearConstraint(values_issue, Relationship.EQ, higherUtility));

            k += 1;

            for(int pos = 0; pos < vars; pos++)
                previous_values[pos] = values_issue[pos];
        }

        SimplexSolver solver = new SimplexSolver();
        solver.setMaxIterations(Integer.MAX_VALUE);
        PointValuePair solution = solver.optimize(objectiveFunction, constraints, GoalType.MINIMIZE, true);

        int pos = slackvars;
        for(Issue issue: issues) {
            IssueDiscrete issueD = (IssueDiscrete) issue;
            for(ValueDiscrete valueD: issueD.getValues()) {
                this.setUtility(issue, valueD, solution.getPoint()[pos]);
                pos+=1;
            }
        }

        this.normalizeWeights();
    }

    private double[] get_values_bid(Bid b, int numberSlacks, int arraySize) {
        double [] values = new double[arraySize];

        for(Issue issue: b.getIssues()) {
            int issueNumber = issue.getNumber();
            IssueDiscrete issueD = (IssueDiscrete) issue;

            ValueDiscrete v = (ValueDiscrete) b.getValue(issueNumber);

            int index = numberSlacks;
            int key = mapping_issues.get(issueNumber);
            //get the number of issues before this, and respective number of values
            for(int issues = 0; issues < key; issues++)
                index += this.number_values_per_issue.get(issues);

            for(ValueDiscrete valueD: issueD.getValues()) {
                if(valueD.hashCode() == v.hashCode()) {
                    values[index] = 1;
                    break;
                }
                else
                    index += 1;
            }
        }

        return values;
    }
}
