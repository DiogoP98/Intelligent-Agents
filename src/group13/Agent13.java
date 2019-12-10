package group13;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.time.OffsetDateTime;
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
import genius.core.utility.UncertainAdditiveUtilitySpace;
import genius.core.analysis.MultilateralAnalysis;
/**
 * ExampleAgent returns the bid that maximizes its own utility for half of the negotiation session.
 * In the second half, it offers a random bid. It only accepts the bid on the table in this phase,
 * if the utility of the bid is higher than Example Agent's last bid.
 */
public class Agent13 extends AbstractNegotiationParty {
    private final String description = "Winter is upon you";
    private double bestBidUtility;
    private double worstBidUtility;
    private Bid lastReceivedOffer;
    private Bid myLastOffer;
    private OpponentModel opponent;
    private final Map<AgentID, OpponentModel> opponentsModels = new HashMap<>();
    private double concessionRate = 0.08;
    private final Random randomGenerator = new Random();
    private UncertaintyModelling factory;
//    ExperimentalUserModel e;
//    UncertainAdditiveUtilitySpace realUSpace;
    private List<Double> bidHistoryAgent = new ArrayList<>();
    private List<Double> bidHistoryOpponent = new ArrayList<>();
    private double worstRecievedBidUtility = 1;
    private double bestReceivedBidUtility = 0;
    private boolean opponentIsHardHeaded = false;

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);

        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
        Domain domain = getDomain();
        this.opponent = new OpponentModel(domain);
        if (hasPreferenceUncertainty()) {
            this.factory = new UncertaintyModelling(domain);
//            e = ( ExperimentalUserModel ) userModel;
//            realUSpace = e. getRealUtilitySpace();

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
            score += model.getValue(bid);
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
            double percent_diff = (this.utilitySpace.getUtility(myLastOffer) - this.utilitySpace.getUtility(randomBid))/this.utilitySpace.getUtility(myLastOffer);

            if(this.utilitySpace.getUtility(randomBid)>= threshold && percent_diff < 0.1){
                if(!result.contains(randomBid)){
                    deadLimit = -1;
                }
//                System.out.println(this.utilitySpace.getUtility(randomBid));
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
        List <Double> lastNAgentBids;
        List <Double> lastNOpponentBids;
        int n = 15;
        // According to Stacked Alternating Offers Protocol list includes
        // Accept, Offer and EndNegotiation actions only.
        double time = getTimeLine().getTime(); // Gets the time, running from t = 0 (start) to t = 1 (deadline).
        // The time is normalized, so agents need not be
        // concerned with the actual internal clock.

        if (time > 0.85 && time < 0.89 && (bestReceivedBidUtility - worstRecievedBidUtility) <= 0.3) {
//            System.out.println(bestReceivedBidUtility + ": " + worstRecievedBidUtility);
            opponentIsHardHeaded = true;
            concessionRate = 0.05;
        }else if(time > 0.85 && time < 0.89 && (bestReceivedBidUtility - worstRecievedBidUtility) > 0.4){
//            System.out.println(bestReceivedBidUtility + " : " + worstRecievedBidUtility);
            concessionRate = 0.02; // let them conceed
        }

        // First half of the negotiation offering the max utility (the best agreement possible) for Example Agent
        if (time < 0.1) {
            this.myLastOffer = this.getMaxUtilityBid();
            return new Offer(this.getPartyId(), myLastOffer);
        } else {
            try {
                double myUtility = this.utilitySpace.getUtility(lastReceivedOffer);
                double utilityThreshold = getUtilityThreshold() ;

                if (time > 0.8 && lastReceivedOffer != null && opponentIsHardHeaded){
                    double theirUtility = this.opponent.getValue(lastReceivedOffer);
                    try{
                        if(bidHistoryOpponent.size() > n+1){
                            lastNOpponentBids = bidHistoryOpponent.subList(bidHistoryOpponent.size() - n+1, bidHistoryOpponent.size() - 2);
//                            lastNAgentBids = bidHistoryAgent.subList(bidHistoryAgent.size() - n+1, bidHistoryAgent.size() - 2);
                        } else {
                            lastNOpponentBids = bidHistoryOpponent.subList(0, bidHistoryOpponent.size() - 2);
//                            lastNAgentBids = bidHistoryAgent.subList(0, bidHistoryAgent.size() - 2);
                        }
                    } catch (Exception e){
                        lastNOpponentBids = bidHistoryOpponent;
//                        lastNAgentBids = bidHistoryAgent;
                    }

//                    boolean suddenIncrease = detectSuddenChange(lastNAgentBids, myUtility, 3, "upper");
                    boolean suddenDecrease = detectSuddenChange(lastNOpponentBids, theirUtility, 3, "lower");
//                    System.out.println(suddenDecrease + " : " + theirUtility);
                    if (suddenDecrease && (myUtility >= utilityThreshold)){
                        System.out.println("They dropped");
                        return new Offer(this.getPartyId(), lastReceivedOffer);
                    }

//                    if (suddenIncrease && (myUtility >= utilityThreshold)){
//                        System.out.println("Our utility increased");
//                        return new Offer(this.getPartyId(), lastReceivedOffer);
//                    }
                }

                // Accepts the bid on the table in this phase,
                // if the utility of the bid is higher than Example Agent's last bid.
                if (lastReceivedOffer != null
                        && myLastOffer != null
                        && myUtility >= utilityThreshold) {
                    return new Accept(this.getPartyId(), lastReceivedOffer);
                }


                // Generate random bids above threshold
                Set<Bid> bidSet = this.generateBids(utilityThreshold, 500, 10000);

                if(randomGenerator.nextDouble() <= 0.1) {
                    this.myLastOffer =  pickRandomBid(bidSet);
                    return new Offer(this.getPartyId(),this.myLastOffer);
                } else {
                    // java did some weird shit to it, it's basically saying from bidset, compare and get the best one
                    Bid bestBid = Collections.max(bidSet, Comparator.comparingDouble(this::getOpponentScore));
                    this.myLastOffer = bestBid;
                    return new Offer(this.getPartyId(), bestBid);
                }
            } catch (Exception e){
                this.myLastOffer = this.getMaxUtilityBid();
                return new Offer(this.getPartyId(), this.myLastOffer);
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
            lastReceivedOffer = offer.getBid();
            double bidUtility = this.utilitySpace.getUtility(lastReceivedOffer);

            if(bidUtility > bestBidUtility) bestBidUtility = bidUtility;
            if(bidUtility < worstBidUtility) worstBidUtility = bidUtility;

//            if(bidUtility > bestReceivedBidUtility) bestReceivedBidUtility = bidUtility;
//            if(bidUtility < worstRecievedBidUtility) worstRecievedBidUtility = bidUtility;

            opponentsModels.putIfAbsent(sender, new OpponentModel(getDomain()));
            opponentsModels.get(sender).updateFrequency(offer.getBid());
            this.opponent.updateFrequency(offer.getBid());
            // storing last received offer

            if(getTimeLine().getTime() > 0.77){
//                bidHistoryAgent.add(bidUtility);
                bidHistoryOpponent.add(this.opponent.getValue(lastReceivedOffer));
            }

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
        return bestBidUtility - (bestBidUtility - worstBidUtility) * Math.pow(getTimeLine().getTime(), 1 / concessionRate);
    }

    public double[] getStandardDeviation(List<Double> arr){
        double [] ret = {0.0, 0.0};
        double sum = 0.0, standardDeviation = 0.0;
        int length = arr.size();
        for(double num : arr) {
            sum += num;
        }
        double mean = sum/length;
        for(double num: arr) {
            standardDeviation += Math.pow(num - mean, 2);
        }
        ret[0] = mean;
        ret[1] = Math.sqrt(standardDeviation/length);

        return ret;
    }


    public  boolean detectSuddenChange(List<Double> arr, double val, int n, String bound){
        double [] std = getStandardDeviation(arr);
        double sigN = std[1] * n; // std * n

        if (bound.equals("upper")){
            return val >= (sigN + std[0]); // 3sig + mean
        }
        return val <=(- sigN + std[0]); // 3sig - mean
    }

    public Bid pickRandomBid(Set<Bid> bidSet){
        List<Bid> list = new ArrayList<Bid>(bidSet.size());
        list.addAll(bidSet);
        Collections.shuffle(list);
        return list.get(0);
    }
}
