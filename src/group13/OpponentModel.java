package group13;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class OpponentModel {
    private Domain d;
    private HashMap<Integer, Integer> mapping_issues = new HashMap<Integer, Integer>();
    private HashMap<String, Integer> mapping_values = new HashMap<String, Integer>();
    private Integer[][] frequency;
    private Double[] weights;
    private int numberOfBids;

    public OpponentModel(Domain d) {
        this.d = d;

        buildData();
    }

    public Double updateFrequency(Bid b) {
        this.numberOfBids += 1;
        List<Issue> issues = b.getIssues();

        Integer[] valuesUsed = new Integer[issues.size()]; //values for each issue used on the bid

        for(Issue i: issues) {
            int issueNumber = i.getNumber();
            Integer issueKey =this.mapping_issues.get(issueNumber);
            ValueDiscrete v = (ValueDiscrete) b.getValue(issueNumber);
            Integer valueKey = this.mapping_values.get(v.toString() + String.valueOf(issueNumber));

            valuesUsed[issueKey] = valueKey;
            this.frequency[issueKey][valueKey] += 1;
        }

        double utility = updateOpponentModel(issues, valuesUsed);

        return utility;
    }

    private double updateOpponentModel(List<Issue> issues, Integer[] valuesUsed) {
        double [] predictedValues = updateWeightsAndOrder(issues, valuesUsed);
        double utility = 0;

        for(int i = 0; i < issues.size(); i++)
            utility += this.weights[i]*predictedValues[i];

        return utility;
    }

    private double[] updateWeightsAndOrder(List<Issue> issues, Integer[] valuesUsed) {
        double [] weightsIntermediate = new double[issues.size()];
        double [] PredictedValueOfOption = new double[issues.size()];

        for(Issue i: issues) {
            int issueNumber = i.getNumber();
            IssueDiscrete issueDiscrete = (IssueDiscrete) i;

            Integer issueKey =this.mapping_issues.get(issueNumber);

            PredictedValueOfOption[issueKey] = getValuesOfOption(issueKey, valuesUsed[issueKey]);

            for(ValueDiscrete v: issueDiscrete.getValues()) {
                Integer valueKey = this.mapping_values.get(v.toString() + String.valueOf(issueNumber));
                Integer freq = this.frequency[issueKey][valueKey];
                weightsIntermediate[issueKey] += (Math.pow(freq,2.0)) / (Math.pow(this.numberOfBids,2.0));
            }
        }

        for(Issue i: issues) {
            Double sum = 0.0;
            int issueNumber = i.getNumber();
            Integer issueKey1 = this.mapping_issues.get(issueNumber);

            for(Issue j: issues) {
                int issueNumber2 = j.getNumber();
                Integer issueKey2 = this.mapping_issues.get(issueNumber2);

                sum += weightsIntermediate[issueKey2];
            }

            this.weights[issueKey1] = weightsIntermediate[issueKey1] / sum;
        }

        return PredictedValueOfOption;
    }

    private double getValuesOfOption(Integer issueKey, Integer valueKey) {
        int order = 1;
        int size = frequency[issueKey].length;
        for(int i = 0; i < size; i++) {
            if(i != valueKey && frequency[issueKey][i] > frequency[issueKey][valueKey])
                order += 1;
        }

        return  (double) (size - order + 1) / size;
    }

    private void buildData() {
        this.numberOfBids = 0;

        List<Issue> issues = this.d.getIssues();

        this.frequency = new Integer[issues.size()][];
        this.weights = new Double[issues.size()];

        Integer count_issues = 0;
        for (Issue issue : issues) {
            int issueNumber = issue.getNumber();

            this.mapping_issues.put(issueNumber, count_issues);
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;

            this.frequency[count_issues] = new Integer[issueDiscrete.getValues().size()];
            Integer count_values = 0;

            for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
                this.mapping_values.put(valueDiscrete.toString()+ String.valueOf(issueNumber), count_values);

                this.frequency[count_issues][count_values] = 0;

                count_values++;
            }
            count_issues++;
        }
    }


}
