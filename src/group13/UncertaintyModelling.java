package group13;

import genius.core.Domain;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AdditiveUtilitySpace;
import scpsolver.problems.LinearProgram;

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

    @Override
    public void estimateUsingBidRanks(BidRanking b) {
        LinearProgram lp = new LinearProgram(new double[]{5.0,10.0});
    }
}
