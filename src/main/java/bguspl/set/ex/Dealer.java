package bguspl.set.ex;

import bguspl.set.Env;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime;

    /**
     * The thread representing the dealer.
     */
    private Thread dealerThread;

    /**
     * represents all the cards from the deck and the table.
     */
    private List<Integer> remainingCards;

    /**
     * The players the dealer needs to check their set.
     */
    ConcurrentLinkedQueue<Player> playersToCheck;

    /*
     * Sleeping time of the dealer.
     */
    private long DEALER_SLEEPING_TIME;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.DEALER_SLEEPING_TIME = Math.min(950, env.config.turnTimeoutMillis);
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.playersToCheck = new ConcurrentLinkedQueue<Player>();
        this.remainingCards = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        // creating and starting players threads
        for(int i = 0; i < players.length; i++){
            Thread PlayerThread = new Thread(players[i], env.config.playerNames[i]);              
            players[i].setPlayerThread(PlayerThread);
            PlayerThread.start();
        }

        while (!shouldFinish()) {  //loop for new 60 seconds.
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
        }

        announceWinners();
        this.terminate();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");

    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() { //loop for while 60 seconds.
        while (!terminate && System.currentTimeMillis() < reshuffleTime) { 
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
        }
    }

    /**
     * Called when the game should be terminated.
     * @throws InterruptedException
     */
    public void terminate(){
        try {
            players[0].terminate();
            players[0].getPlayerThread().join();
        } catch (InterruptedException e) {}
        this.terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     * 
     */
    boolean shouldFinish() {
        return terminate || env.util.findSets(remainingCards, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable(int[] slots) {
        synchronized(table){
            for(int i=0; i < slots.length; i++){
                for(Player player : players){
                    player.removeToken(slots[i]);
                }
                remainingCards.remove(this.table.slotToCard[slots[i]]);
                this.table.removeCard(slots[i]);
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        synchronized(table){
            for(int i=0; i < this.table.slotToCard.length; i++){
                if(!this.deck.isEmpty() && this.table.slotToCard[i] == null){
                    Random rand = new Random();
                    Integer randCard = deck.get(rand.nextInt((deck.size())));
                    table.placeCard(randCard, i);
                    deck.remove(randCard);
                }
            }
        }       
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        long START_TIME = System.currentTimeMillis();
            while(System.currentTimeMillis() < START_TIME + DEALER_SLEEPING_TIME){
                try{
                    synchronized(this){
                        boolean wakeUp = false;
                        while(this.playersToCheck.size() == 0 && !wakeUp){ 
                            this.wait(Math.max(DEALER_SLEEPING_TIME - (System.currentTimeMillis() - START_TIME),1));
                            wakeUp = true;
                        }
                        if(this.playersToCheck.size() > 0){
                            this.checkSet(); 
                        }
                    }
                } catch(InterruptedException ignored){}
            }   
    }

    private void checkSet(){ 
        final int LEGAL_SET = 1;
        final int ILLEGAL_SET = 2;
        final int OTHER_PLAYER_WITH_SAME_CARD_CASE = 3;
        

        if(!playersToCheck.isEmpty()){
            Player player = playersToCheck.poll();

            if(player.getTokenList().size() == env.config.featureSize){
                int[] playerSet = new int[env.config.featureSize];
                for(int i=0; i < env.config.featureSize ; i++){
                    playerSet[i] = this.table.getCardFromSlot(player.getTokenList().get(i));
                }

                boolean isLegal = env.util.testSet(playerSet);
                
                if(isLegal){
                    synchronized(player){
                        player.setFlag(LEGAL_SET); //legal set.
                        player.notifyAll();
                    }
                    synchronized(table){
                        removeCardsFromTable(player.setListToArray(player.getTokenList()));
                        placeCardsOnTable();
                    }
                    updateTimerDisplay(true); //Reset the TurnTimeoutSeconds.
                }
                
                else{  
                    synchronized(player){
                        player.setFlag(ILLEGAL_SET); //illegal set.  
                        player.notifyAll();
                    }
                }
            }
            else{ // if the player have only two/one/zero cards. (another player did set with same cards and has checked before)
                synchronized(player){ 
                    player.setFlag(OTHER_PLAYER_WITH_SAME_CARD_CASE);
                    player.notifyAll();
                }
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset){
            this.reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
            DEALER_SLEEPING_TIME = Math.min(950, env.config.turnTimeoutMillis);
        }
        else if(reshuffleTime - System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis){
            env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis(),0), true);
            DEALER_SLEEPING_TIME = 1;
        }
        else{ 
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    void removeAllCardsFromTable() {
        final int TABLE_SIZE = env.config.columns*env.config.rows;
        synchronized(table){
            for(int i = 0; i < TABLE_SIZE; i++){
                for(Player player : players){
                    player.removeToken(i);
                }
                // Checking if != Null for the ending of the game (when there are not 12 cards on the table).
                if(this.table.slotToCard[i] != null){ 
                    deck.add(this.table.slotToCard[i]);
                    this.table.removeCard(i); 
                }
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int max = -1;
        for(Player player : players){
            if(player.score() > max){
                max = player.score();
            }
        }
        int count = 0;
        for(Player player : players){
            if(player.score() == max){
               count++;
            }
        }

        int[] winners = new int[count];
        int i = 0;
        for(Player player : players){
            if(player.score() == max){
                winners[i] = player.id;
                i++;
            }
        }
        this.env.ui.announceWinner(winners);
    }
    
    /**
     * adding the player to the queue of players the dealer needs to check their set
     * @pre - the queue of playersToCheck is initiallized
     * @post - the size of the queue of playersToCheck is increased by 1 and the dealer is notified that there is a set he needs to check
     */
    public void addPlayerToCheck(Player player){
        synchronized(this){
            playersToCheck.add(player);
            this.notifyAll();
        }
    }

    public Player[] getPlayersArray(){
        return players;
    }
}
