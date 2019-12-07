package group13;
import genius.core.Bid;
import genius.core.BidIterator;
import genius.core.Domain;
import genius.core.utility.UtilitySpace;


import java.util.ArrayList;
import java.util.List;

public class Nash {

    private Domain domain;
    private UtilitySpace agentUtilitySpace;
    private OpponentModel opponentModel;
    private List<BidPoint> bidSpace; // All bid points
    private List<BidPoint> paretoFrontier;
    private BidPoint nashPoint;
    private static final int ITERATION_LIMIT = 25000; // Maximum number of iterations before it cuts off
    private boolean bidSpaceUpdated;

    public Nash(Domain domain, UtilitySpace agentUtilitySpace, OpponentModel opponentModel) {
        this.domain = domain;
        this.agentUtilitySpace = agentUtilitySpace;
        this.opponentModel = opponentModel;
        paretoFrontier = new ArrayList<>();
    }


    private void createBidSpace() {
        bidSpace = new ArrayList<>();
        BidIterator bids = new BidIterator(domain);
        int i = 0;

        while (bids.hasNext() && i < ITERATION_LIMIT) {
            Bid bid = bids.next();
            bidSpace.add(new BidPoint(bid, agentUtilitySpace.getUtility(bid), opponentModel.updateFrequency(bid)));
            i++;
        }
        bidSpaceUpdated = true;
    }


    public void updateBidSpace(OpponentModel opponentModel) {
        this.opponentModel = opponentModel;
        if (bidSpace == null) {
            createBidSpace();
        } else {
            for (BidPoint bp : bidSpace) {
                bp.setOpponentUtility(this.opponentModel.updateFrequency(bp.getBid()));
            }
        }
        bidSpaceUpdated = true;
    }


    public Bid getNashPoint(){
        // Only compute if bid space has been updated since last computation or no Nash point exists
        if (bidSpaceUpdated || nashPoint == null) {
            bidSpaceUpdated = false;

            // Calculate pareto frontier by finding strictly dominating bids
            for (BidPoint bp : bidSpace) {
                addToFrontier(bp);
            }

            double maxUtilityProduct = -1;
            double currentUtilityProduct = 0;
            // Loop through pareto frontier and find Nash point
            for (BidPoint bp : paretoFrontier) {
                currentUtilityProduct = bp.getUtilityProduct();
                if (currentUtilityProduct > maxUtilityProduct) {
                    nashPoint = bp;
                    maxUtilityProduct = currentUtilityProduct;
                }
            }
        }

        return nashPoint == null ? null : nashPoint.getBid();
    }


    private void addToFrontier(BidPoint bp) {
        for (BidPoint f : paretoFrontier) {
            // If this bid point is dominated, it can't be added so do nothing
            // If a point in the frontier is dominated, it needs replacing
            if (f.dominatedBy(bp)) {
                removeDominated(bp, f);
            }
        }
        // Otherwise the bid must have been equal so add anyway
        paretoFrontier.add(bp);
    }

    /**
     * Removing a dominated bid from the frontier and replacing it with the new dominating bid
     */
    private void removeDominated(BidPoint bidPointToAdd, BidPoint bidPointToRemove) {
        // A bid has been dominated - the old one needs removing and the new one adding
        List<BidPoint> frontierPointsToRemove = new ArrayList<>();
        paretoFrontier.remove(bidPointToRemove);
        // Check if any others are also dominated by this new bid
        for (BidPoint b : paretoFrontier) {
            if (b.dominatedBy(bidPointToAdd)) {
                frontierPointsToRemove.add(b);
            }
        }
        paretoFrontier.removeAll(frontierPointsToRemove);
        paretoFrontier.add(bidPointToAdd);
    }

    public double getEuclideanDistance(Bid b) {
        if (nashPoint != null) {
            double agentUtilDiff = nashPoint.getAgentUtility() - agentUtilitySpace.getUtility(b);
            double opponentUtilDiff = nashPoint.getOpponentUtility() - opponentModel.updateFrequency(b);
            // calculate Euclidean distance to Nash point
            return Math.sqrt(((Math.pow(agentUtilDiff, 2)) + (Math.pow(opponentUtilDiff, 2))));
        }
        return -1; // cant use 0 as it'd mean its the best point
    }


    public double getNashUtility() {
        return nashPoint.getAgentUtility();
    }
}