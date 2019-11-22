package group13;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.Issue;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.Evaluator;
import scpsolver.constraints.LinearBiggerThanEqualsConstraint;
import scpsolver.constraints.LinearSmallerThanEqualsConstraint;
import scpsolver.problems.LinearProgram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UncertaintyModelling extends AdditiveUtilitySpaceFactory {
    private final Domain domain;

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
        estimateUsingBidRanks(bids); //to get the evaluation for the values of each issue
        AdditiveUtilitySpace u = getUtilitySpace();
        double [] variables = new double[(bids.getSize() - 1) * 2];

        for(Bid b: bids.getBidOrder()) {
            double [] values_issue = new double[this.domain.getIssues().size()];
            int j = 0;
            for(Issue i: getIssues()) {
                Evaluator evaluator = u.getEvaluator(i);
                double value = evaluator.getEvaluation(u, b, i.getNumber());
                values_issue[j++] = value;
            }
            LinearProgram lp = new LinearProgram(new double[]{1.0,1.0});
            lp.addConstraint(new LinearBiggerThanEqualsConstraint(values_issue, 8.0, "c1"));
            lp.addConstraint(new LinearBiggerThanEqualsConstraint(new double[]{0.0,4.0}, 4.0, "c2"));
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(new double[]{2.0,0.0}, 2.0, "c3"));
            double [] values_issue_previous = values_issue;

        }

    }

    public void estimateUsingBidRanks_(BidRanking bids) {
        double [] weights;
        int issuesIndex = 0;
        HashMap<Integer, String> issuesMap = new HashMap<>();
        double points = 0;
        for (Bid b: bids.getBidOrder()) {
            List<Issue> issues = b.getIssues();
            for(Issue i : issues){
                int no = i.getNumber();
                ValueDiscrete v = (ValueDiscrete) b.getValue(no);
                double oldUtil = getUtility(i,v);
                setUtility(i, v, oldUtil + points);
                if(!issuesMap.containsKey(no)){
                    issuesMap.put(no, Integer.toString(issuesIndex));
                }
            }
            points =+ 1;
        }


    }
}
