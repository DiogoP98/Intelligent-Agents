package group13;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.Evaluator;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.linear.*;
import org.apache.commons.math3.optimization.GoalType;

import java.util.*;

@SuppressWarnings("deprecation")

public class UncertaintyModelling extends AdditiveUtilitySpaceFactory {
    private final Domain domain;
    private HashMap<Integer, Integer> mapping_issues = new HashMap<>();
    HashMap<Integer, Integer> number_values_per_issue = new HashMap<>();

    AdditiveUtilitySpace u;
    /**
     * Generates an simple Utility Space on the domain, with equal weights and zero values.
     * Everything is zero-filled to already have all keys contained in the utility maps.
     *
     * @param d
     */
    public UncertaintyModelling(Domain d) {
        super(d);
        this.domain = d;
    }

    public void UncertaintyEstimation(BidRanking bids) {
        double points = 0;

        List<Bid> Bids = bids.getBidOrder();
        double lowerUtility = bids.getLowUtility();
        double higherUtility = bids.getHighUtility();
        estimateUsingBidRanks(bids);


        LP(Bids, lowerUtility, higherUtility);

    }

    private void LP(List<Bid> Bids, double lowerUtility, double higherUtility) {
        this.u = getUtilitySpace();

        List<Issue> issues = this.getDomain().getIssues();

        int vars = 0;
        int key = 0;
        for(Issue issue: issues) {
            int issueNumber = issue.getNumber();
            IssueDiscrete issueD = (IssueDiscrete) issue;
            int nvalues_issue = issueD.getValues().size();
            this.number_values_per_issue.put(key,nvalues_issue);
            mapping_issues.put(issueNumber, key++);
            vars += nvalues_issue;
        }

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
            if(k == 0) {
                constraints.add(new LinearConstraint(values_issue, Relationship.EQ, lowerUtility));
            }
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
            int issueNumber = issue.getNumber();
            IssueDiscrete issueD = (IssueDiscrete) issue;
            for(ValueDiscrete valueD: issueD.getValues()) {
                setUtility(issue, valueD, solution.getPoint()[pos]);
                pos+=1;
            }
        }

        normalizeWeights();
    }

    private double[] get_values_bid(Bid b, int numberSlacks, int arraySize) {
        double [] values = new double[arraySize];

        for(Issue issue: b.getIssues()) {
            Evaluator evaluator = this.u.getEvaluator(issue);
            int issueNumber = issue.getNumber();
            IssueDiscrete issueD = (IssueDiscrete) issue;

            ValueDiscrete v = (ValueDiscrete) b.getValue(issueNumber);
            double value = evaluator.getEvaluation(u, b, issueNumber);

            int index = numberSlacks;
            int key = mapping_issues.get(issueNumber);
            //get the number of issues before this, and respective number of values
            //values[key] = value;
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
