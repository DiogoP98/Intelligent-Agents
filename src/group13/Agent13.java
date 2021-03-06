package group13;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.Domain;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.BidRanking;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;


public class Agent13 extends AbstractNegotiationParty {
    private final String description = "Winter is upon you";
    private double bestBidUtility;
    private double worstBidUtility;
    private Bid lastReceivedOffer;
    private Bid myLastOffer;
    private OpponentModel opponent;
    private final Map<AgentID, OpponentModel> opponentsModels = new HashMap<>();
    private double concessionRate = 0.2;
    private final Random randomGenerator = new Random();
    private UncertaintyModelling factory;
    private List<Double> bidHistoryOpponent = new ArrayList<>();
    private double worstRecievedBidUtility = 1;
    private double bestReceivedBidUtility = 0;
    private boolean opponentIsHardHeaded = false;
    private Domain domain;

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);

        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
        this.domain = getDomain();
        this.opponent = new OpponentModel(this.domain);
        if (hasPreferenceUncertainty()) {
            this.factory = new UncertaintyModelling(this.domain);
            BidRanking bidRanking = userModel.getBidRanking();
            Bid worstBid = bidRanking.getMinimalBid();
            worstBidUtility = this.utilitySpace.getUtility(worstBid);
            Bid bestBid = bidRanking.getMaximalBid();
            bestBidUtility = this.utilitySpace.getUtility(bestBid);
            this.factory.UncertaintyEstimation(bidRanking);
        }

    }

    /**
     * @param bid: take in the current bid
     * @return return score for opponent using opponent modelling
     */
    private double getOpponentScore(Bid bid){
        double score = 0;
        for(OpponentModel model : opponentsModels.values()){
            score += model.getValue(bid);
        }
        return score;
    }


    /**
     * @param threshold : utility threshold for this to be more than utility
     * @param noOfBids : number of bids to generate
     * @param limit : limit for iterations
     * @return : Set of bids that's higher than threshold
     */
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
                    deadLimit -= 1;
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
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {
        List <Double> lastNOpponentBids;
        int n = 15;
        // According to Stacked Alternating Offers Protocol list includes
        // Accept, Offer and EndNegotiation actions only.
        double time = getTimeLine().getTime(); // Gets the time, running from t = 0 (start) to t = 1 (deadline).
        // The time is normalized, so agents need not be
        // concerned with the actual internal clock.

        if (time > 0.85 && time < 0.89 && (bestReceivedBidUtility - worstRecievedBidUtility) <= 0.3) {
            opponentIsHardHeaded = true;
            concessionRate = 0.04;
        }else if(time > 0.85 && time < 0.89 && (bestReceivedBidUtility - worstRecievedBidUtility) > 0.4){
            concessionRate = 0.02; // let them conceed
        }

        // First 20% of the negotiation offering the max utility (the best agreement possible) for Example Agent
        if (time < 0.2 || this.opponent.numberOfBids < 10) {
            this.myLastOffer = this.getMaxUtilityBid();
            return new Offer(this.getPartyId(), myLastOffer);
        } else {
            try {
                double myUtility = this.utilitySpace.getUtility(lastReceivedOffer) + 0.1;
                double utilityThreshold = getUtilityThreshold();
                if (time > 0.7 && lastReceivedOffer != null && opponentIsHardHeaded){
                    double theirUtility = this.opponent.getValue(lastReceivedOffer);
                    try{
                        if(bidHistoryOpponent.size() > n+1){
                            lastNOpponentBids = bidHistoryOpponent.subList(bidHistoryOpponent.size() - n+1, bidHistoryOpponent.size() - 2);
                        } else {
                            lastNOpponentBids = bidHistoryOpponent.subList(0, bidHistoryOpponent.size() - 2);
                        }
                    } catch (Exception e){
                        lastNOpponentBids = bidHistoryOpponent;
                    }

                    boolean suddenDecrease = detectSuddenChange(lastNOpponentBids, theirUtility, 3, "lower");
                    if (suddenDecrease && (myUtility >= utilityThreshold)){
                        return new Offer(this.getPartyId(), lastReceivedOffer);
                    }

                }

                // Accepts the bid on the table in this phase,
                // if the utility of the bid is higher than Example Agent's last bid.
                if (lastReceivedOffer != null
                        && myLastOffer != null
                        && myUtility  >= utilityThreshold) {
                    return new Accept(this.getPartyId(), lastReceivedOffer);
                }


                // Generate random bids above threshold
                Set<Bid> bidSet = this.generateBids(utilityThreshold, 10, 1000);

                if(randomGenerator.nextDouble() <= 0.01) {
                    this.myLastOffer =  pickRandomBid(bidSet);
                    return new Offer(this.getPartyId(),this.myLastOffer);
                } else {
                    // from bidset, compare and get the best one
                    Bid randomBid = Collections.max(bidSet, Comparator.comparingDouble(this::getOpponentScore));
                    Bid nashBid = getNash(bidSet);
                    double randomBidUtility = this.utilitySpace.getUtility(randomBid);
                    double nashBidUtility = this.utilitySpace.getUtility(nashBid);
                    Bid bestBid = randomBidUtility > nashBidUtility ? randomBid : nashBid; // best bid is whatever is higher
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
     * @param sender : opponent Agent ID
     * @param act : action taken by the opponent (includes their offer)
     */
    @Override
    public void receiveMessage(AgentID sender, Action act) {
        super.receiveMessage(sender, act);

        if (act instanceof Offer) { // sender is making an offer
            Offer offer = (Offer) act;
            lastReceivedOffer = offer.getBid();
            double time = getTimeLine().getTime();

            double bidUtility = this.utilitySpace.getUtility(lastReceivedOffer);


            if(bidUtility > bestReceivedBidUtility) bestReceivedBidUtility = bidUtility;
            if(bidUtility < worstRecievedBidUtility) worstRecievedBidUtility = bidUtility;

            //If time is close to deadline don't update opponent model because it might take too long
            if(time < 0.95){
                opponentsModels.putIfAbsent(sender, new OpponentModel(getDomain()));
                opponentsModels.get(sender).updateFrequency(offer.getBid());
                this.opponent.updateFrequency(offer.getBid());
            }

            // storing last received offer
            if(time > 0.65){
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


    // get utility threshold according to the paper
    public double getUtilityThreshold(){
        return bestBidUtility - (bestBidUtility - worstBidUtility) * Math.pow(getTimeLine().getTime(), 1 / concessionRate);
    }


    /**
     * @param lstDoubles : get a list of doubles
     * @return : standard deviation of the list
     */
    public double[] getStandardDeviation(List<Double> lstDoubles){
        double [] ret = {0.0, 0.0};
        double sum = 0.0, standardDeviation = 0.0;
        int length = lstDoubles.size();
        for(double num : lstDoubles) {
            sum += num;
        }
        double mean = sum/length;
        for(double num: lstDoubles) {
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

    public Bid getNash(Set <Bid> bids) {
        double reservationValue = this.factory.getUtilitySpace().getReservationValue();
        double bestUtility = 0.0;
        Bid nash = bids.iterator().next();
        for(Bid bid: bids) {
            double utility = (this.opponent.getValue(bid) - reservationValue) * (this.factory.getUtilitySpace().getUtility(bid) - reservationValue);
            if(utility > bestUtility) {
                bestUtility = utility;
                nash = bid;
            }
        }
        return nash;
    }
}
