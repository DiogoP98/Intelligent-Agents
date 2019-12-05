package group13;

import genius.core.Bid;

public class BidPoint {

    private Bid bid;
    private double agentUtility;
    private double opponentUtility;

    public BidPoint(Bid bid, double agentUtility, double opponentUtility) {
        this.bid = bid;
        this.agentUtility = agentUtility;
        this.opponentUtility = opponentUtility;
    }


    public boolean dominatedBy(BidPoint agent) {
        if (agent != this) {
            if ((agent.getAgentUtility() < getAgentUtility()) || (agent.getOpponentUtility() < getOpponentUtility())) {
                // One of the utilities is smaller
                return false;
            } if ((agent.getAgentUtility() > getAgentUtility()) || (agent.getOpponentUtility() > getOpponentUtility())) {
                // At least one utility is strictly greater
                return true;
            }
        }
        return false;
    }

    public double getUtilityProduct() {
        return agentUtility * opponentUtility;
    }

    public Bid getBid() {
        return bid;
    }

    public double getAgentUtility() {
        return agentUtility;
    }

    public double getOpponentUtility() {
        return opponentUtility;
    }

    public void setOpponentUtility(double utility) {
        this.opponentUtility = utility;
    }
}