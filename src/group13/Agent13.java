package group13;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.Domain;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.ExperimentalUserModel;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.UncertainAdditiveUtilitySpace;

/**
 * ExampleAgent returns the bid that maximizes its own utility for half of the negotiation session.
 * In the second half, it offers a random bid. It only accepts the bid on the table in this phase,
 * if the utility of the bid is higher than Example Agent's last bid.
 */
public class Agent13 extends AbstractNegotiationParty {
    private final String description = "It's changing";
    private double bestBidUtility;
    private double worstBidUtility;
    private Bid lastReceivedOffer;
    private Bid myLastOffer;
    private OpponentModel opponent;
    private final Map<AgentID, OpponentModel> opponentsModels = new HashMap<>();
    private final double concessionRate = 0.3;
    private final Random randomGenerator = new Random();
    private UncertaintyModelling factory;
    ExperimentalUserModel e = ( ExperimentalUserModel ) userModel ;
    UncertainAdditiveUtilitySpace realUSpace;

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);

        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
        Domain domain = getDomain();

        if (hasPreferenceUncertainty()) {
            this.factory = new UncertaintyModelling(domain);
            //realUSpace = e. getRealUtilitySpace();
            this.opponent = new OpponentModel(domain);

            BidRanking bidRanking = userModel.getBidRanking();
            Bid worstBid = bidRanking.getMinimalBid();
            worstBidUtility = this.utilitySpace.getUtility(worstBid);
            Bid bestBid = bidRanking.getMaximalBid();
            bestBidUtility = this.utilitySpace.getUtility(bestBid);
            this.factory.UncertaintyEstimation(bidRanking);
        }

    }

    private double getOpponentScore(Bid bid){
        double score = 0;
        for(OpponentModel model : opponentsModels.values()){
            score += model.updateFrequency(bid);
        }
        return score;
    }


    public Set<Bid> generateBids(double threshold, int noOfBids, int limit){
        Set<Bid> result = new HashSet<>();
        result.add(this.getMaxUtilityBid()); // propose the best bid

        if(threshold < worstBidUtility || threshold > bestBidUtility){
            return  result;
        }

        // stop if we spend 1/10 of allowed time without finding anything
        int deadLimit = limit/10;
        int count = 0;
        int limitCount = 0;

        do{
            Bid randomBid = generateRandomBid();
            if(this.utilitySpace.getUtility(randomBid)>= threshold){
                if(!result.contains(randomBid)){
                    deadLimit = -1;
                }
                result.add(randomBid);
            }
            count++;
            limitCount++;
        } while (result.size() < noOfBids && count < limit && limitCount < deadLimit);

        return result;
    }


    /**
     * When this function is called, it is expected that the Party chooses one of the actions from the possible
     * action list and returns an instance of the chosen action.
     *
     * @param list
     * @return
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {
        // According to Stacked Alternating Offers Protocol list includes
        // Accept, Offer and EndNegotiation actions only.
        double time = getTimeLine().getTime(); // Gets the time, running from t = 0 (start) to t = 1 (deadline).
        // The time is normalized, so agents need not be
        // concerned with the actual internal clock.


        // First half of the negotiation offering the max utility (the best agreement possible) for Example Agent
        if (time < 0.2) {
            return new Offer(this.getPartyId(), this.getMaxUtilityBid());
        } else {
            double utilityThreshold = getUtilityThreshold();
            // Accepts the bid on the table in this phase,
            // if the utility of the bid is higher than Example Agent's last bid.
            if (lastReceivedOffer != null
                    && myLastOffer != null
                    && this.utilitySpace.getUtility(lastReceivedOffer) >= utilityThreshold) {
                return new Accept(this.getPartyId(), lastReceivedOffer);
            }

            // Generate random bids above threshold
            Set<Bid> bidSet = this.generateBids(utilityThreshold, 30, 10000);

            if(randomGenerator.nextDouble() <= 0.05) { // randomly bid .5% chance of doing that
                return new Offer(this.getPartyId(), pickRandomBid(bidSet));
            } else {
                // java did some weird shit to it, it's basically saying from bidset, compare and get the best one
                Bid bestBid = Collections.max(bidSet, Comparator.comparingDouble(this::getOpponentScore));
                return new Offer(this.getPartyId(), bestBid);
            }
        }
    }

    /**
     * This method is called to inform the party that another NegotiationParty chose an Action.
     * @param sender
     * @param act
     */
    @Override
    public void receiveMessage(AgentID sender, Action act) {
        super.receiveMessage(sender, act);

        if (act instanceof Offer) { // sender is making an offer
            Offer offer = (Offer) act;

            opponentsModels.putIfAbsent(sender, new OpponentModel(getDomain()));
            opponentsModels.get(sender).updateFrequency(offer.getBid());
            // storing last received offer
            lastReceivedOffer = offer.getBid();


        }
    }


    @Override
    public String getDescription() {
        return description;
    }

    private Bid getMaxUtilityBid() {
        try {
            return this.utilitySpace.getMaxUtilityBid();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public double getUtilityThreshold(){
        return worstBidUtility - (worstBidUtility - bestBidUtility) * Math.pow(getTimeLine().getTime(), 1 / concessionRate); // no idea why it's calculated this way
    }


    public Bid pickRandomBid(Set<Bid> bidSet){
        List<Bid> list = new ArrayList<Bid>(bidSet.size());
        list.addAll(bidSet);
        Collections.shuffle(list);
        return list.get(0);
    }
}
