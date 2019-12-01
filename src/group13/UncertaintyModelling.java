package group13;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.Issue;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.Evaluator;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.RealVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class UncertaintyModelling extends AdditiveUtilitySpaceFactory {
    private final Domain domain;
    private HashMap<Integer, Integer> mapping_issues = new HashMap<>();
    private HashMap<String, Integer> mapping_z = new HashMap<String, Integer>();

    private RealVar[] issueWeights;
    private RealVar[] zvars;

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

        for (Bid b : Bids) {
            List<Issue> issues = b.getIssues();
            for (Issue i : issues) {
                int no = i.getNumber();
                ValueDiscrete v = (ValueDiscrete) b.getValue(no);
                double oldUtil = getUtility(i, v);
                setUtility(i, v, oldUtil + points);
            }
            points++;
        }

        LP(Bids);

    }

    private void LP(List<Bid> Bids) {
        AdditiveUtilitySpace u = getUtilitySpace();

        Model model = new Model();
        int key = 0;

        issueWeights = new RealVar[getDomain().getIssues().size()];
        zvars = new RealVar[Bids.size() - 1];

        List<Issue> issue = this.getDomain().getIssues();

        for(Issue i : issue) {
            int issueNumber = i.getNumber();
            issueWeights[key] = model.realVar("I"+ String.valueOf(key), 0.0, 1.0,0.0005d);
            mapping_issues.put(issueNumber, key++);
        }

        key = 0;
        for(int i = 0; i < Bids.size() - 1; i++)
            zvars[key] = model.realVar("I"+ String.valueOf(key), 0.0, 1.0,0.0005d);

        double [] previous_values = new double[this.domain.getIssues().size()];
        double [] values_issue = new double[this.domain.getIssues().size()];
        double [] weights = new double[this.domain.getIssues().size()];
        int k = 0;

        for(Bid b: Bids) {
            int j = 0;
            for(Issue i: b.getIssues()) {
                Evaluator evaluator = u.getEvaluator(i);
                int issueNumber = i.getNumber();
                double value = evaluator.getEvaluation(u, b, i.getNumber());
                values_issue[mapping_issues.get(issueNumber)] = value;
            }

            if(k > 0) {
                for(Issue i: issue) {
                    int issueNumber = i.getNumber();
                    Integer map_key = mapping_issues.get(issueNumber);
                    weights[map_key] = values_issue[map_key] - previous_values[map_key];
                }

                model.post(new Constraint("MyConstraint" + String.valueOf(k-1), new MyPropagator(issueWeights, weights, 0.0)));

            }

            if(k == Bids.size() - 1)
                model.post(new Constraint("MyConstraint" + String.valueOf(k), new MyPropagator2(issueWeights, values_issue, 1.0)));

            k++;

            for(int c = 0; c < values_issue.length; c++)
                previous_values[c] = values_issue[c];

        }

        Solver solver = model.getSolver();
        Solution solution = solver.findSolution();
        /*for(Issue i : issue) {
            int issueNumber = i.getNumber();
            //u.setWeight(i,(double) solution.getRealBounds(issueWeights[mapping_issues.get(issueNumber)])[0]);
            System.out.println(Arrays.toString(solution.getRealBounds(issueWeights[mapping_issues.get(issueNumber)])));
        }*/

        System.out.println(solution);
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
