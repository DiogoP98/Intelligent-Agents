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
import org.chocosolver.solver.variables.RealVar;

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
        estimateUsingBidRanks(bids); //to get the evaluation for the values of each issue
        AdditiveUtilitySpace u = getUtilitySpace();

        Model model = new Model();

        int constraints = 0;
        int key = 0;

        issueWeights = new RealVar[getDomain().getIssues().size()];
        zvars = new RealVar[bids.getSize() - 1];

        List<Issue> issue = this.getDomain().getIssues();

        for(Issue i : issue) {
            int issueNumber = i.getNumber();
            issueWeights[key] = model.realVar("I"+ String.valueOf(key), 0.0, 1.0,0.05d);
            mapping_issues.put(issueNumber, key++);
        }

        key = 0;
        for(int i = 0; i < bids.getSize() - 1; i++) {
            zvars[key] = model.realVar("I"+ String.valueOf(key), 0.0, 1.0,0.05d);
        }


       /* double [] variables = new double[(bids.getSize() - 1) + this.domain.getIssues().size()];
        Arrays.fill(variables, 0, bids.getSize() - 2, 1);
        LinearProgram lp = new LinearProgram(variables);

        double [] slacks = new double[(bids.getSize() - 1)];
        int k = 0;
        slacks[0] = 1;

        double [] weightszero = new double[this.domain.getIssues().size()];
        double [] zzero = new double[(bids.getSize() - 1)];

        double [] previous_values = new double[this.domain.getIssues().size()];
        double [] values_issue = new double[this.domain.getIssues().size()];
        double [] weights = new double[this.domain.getIssues().size()];
        double [] vars = new double [(bids.getSize() - 1) + this.domain.getIssues().size()];*/

        double [] previous_values = new double[this.domain.getIssues().size()];
        double [] values_issue = new double[this.domain.getIssues().size()];
        double [] weights = new double[this.domain.getIssues().size()];

       int k = 0;

        for(Bid b: bids.getBidOrder()) {
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


                //System.arraycopy(slacks, 0, vars, 0, (bids.getSize() - 1));
                //System.arraycopy(weights, 0, vars, (bids.getSize() - 1), this.domain.getIssues().size());

                //System.arraycopy(slacks, 0, vars, 0, (bids.getSize() - 1));
                //System.arraycopy(weightszero, 0, vars, (bids.getSize() - 1), this.domain.getIssues().size());

                //lp.addConstraint(new LinearBiggerThanEqualsConstraint(vars, 0.0, "c" + String.valueOf(constraints++)));
                //lp.addConstraint(new LinearBiggerThanEqualsConstraint(vars, 0.0, "c" + String.valueOf(constraints++)));

            }

            if(k == bids.getSize() - 1) {
                //System.arraycopy(zzero, 0, vars, 0, (bids.getSize() - 1));
                //System.arraycopy(values_issue, 0, vars, (bids.getSize() - 1), this.domain.getIssues().size());

                //lp.addConstraint(new LinearEqualsConstraint(vars, 1.0, "c" + String.valueOf(constraints++)));
            }

            //variables[k] = 0;
            //variables[k++] = 1;

            for(int c = 0; c < values_issue.length; c++) {
                //previous_values[c] = values_issue[c];
            }

        }

        //LinearProgramSolver solver  = SolverFactory.newDefault();
        //double[] sol = solver.solve(lp);
        //System.out.println("Finished+++++++");

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
