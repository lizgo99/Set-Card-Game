package bguspl.set.ex;

import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * The dealer of the game.
     */
    private final Dealer dealer;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The tokens that the player has in the table, represent by slots.
     */
    private CopyOnWriteArrayList<Integer> tokensList; 

    /**
     * Actions that the player want to do, represent by slots.
     */
    ConcurrentLinkedQueue<Integer> queueOfActions;

    /**
     * 0 - The player dont have a set / the player need to wait to the dealer for checking his set
     * 1 - The checked set is legal
     * 2 - The checked set is illegal
     * 3 - Other player had set with the same card/s and has checked before this player.
     */
    public volatile int flag ;
   
    /*
     * Lock for the player.
     */
    private Object playerLock;

    

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.queueOfActions = new ConcurrentLinkedQueue<Integer>();
        this.tokensList = new CopyOnWriteArrayList<Integer>();
        this.playerLock = new Object();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();

        try {
        while (!terminate) {
            final int PLAYER_NEEDS_TO_WAIT = 0;
            final int LEGAL_SET = 1;
            final int ILLEGAL_SET = 2;
            final int OTHER_PLAYER_WITH_SAME_CARD_CASE = 3;

            synchronized(playerLock){
                while(queueOfActions.isEmpty()){   
                        playerLock.wait();
                }
            }

            if(!queueOfActions.isEmpty()){
                int slot = queueOfActions.poll();
                if(tokensList.contains((Integer) slot)){                 
                    removeToken(slot);
                }
                else{
                    if(tokensList.size() < env.config.featureSize){
                        placeToken(slot);
                        if(tokensList.size() == env.config.featureSize){
                            dealer.addPlayerToCheck(this);
                            synchronized(this){
                                try {
                                    while(flag == PLAYER_NEEDS_TO_WAIT){
                                        this.wait();
                                    }
                                }
                                catch (InterruptedException e) {}
                                if(flag == LEGAL_SET){
                                    point();
                                }
                                else if(flag == ILLEGAL_SET){
                                    penalty();
                                }
                                else if(flag == OTHER_PLAYER_WITH_SAME_CARD_CASE){
                                    flag = PLAYER_NEEDS_TO_WAIT;
                                }
                            }  
                        }
                    }
                }
            }
        }
    } catch (InterruptedException e) {}
        finished();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /*
     * This function responsable of terminate the threads in reverse order.
     */
    private void finished (){
        if(this.id != (dealer.getPlayersArray().length - 1)){
            try {
                dealer.getPlayersArray()[id+1].terminate();
                dealer.getPlayersArray()[id+1].getPlayerThread().join();
            } catch (InterruptedException e) {}
             
            if(!human){
                try {
                    aiThread.join();
                } catch (InterruptedException e) {}
            }
        }

    }

    
    /**
     * Called when the game should be terminated.
     */
    public void terminate() { 
        playerThread.interrupt(); 
        this.terminate = true;  
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                Random rand = new Random();
                int randSlot = (int) rand.nextInt(env.config.columns*env.config.rows);
                keyPressed(randSlot);
                try{
                    Thread.sleep(0);}
                catch(InterruptedException ex){
                };
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     * 
     * @pre - the players flag need to be set to 0 i.e the player is waiting As long as no keys are pressed
     * @post - the size of queueOfActions in increased by 1 if the the size is less than the number of features and the player is waiting for a key press.
     * 
     */
    public void keyPressed(int slot) {
        int PLAYER_NEEDS_TO_WAIT = 0;

        synchronized(playerLock){
            if(queueOfActions.size() < env.config.featureSize && flag == PLAYER_NEEDS_TO_WAIT){
                queueOfActions.add(slot);
                playerLock.notifyAll(); 
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        final int STOP_SHOW = 0;
        final int SLEEPING_TIME = 950; 
        try{
            env.ui.setFreeze(id, env.config.pointFreezeMillis);
            long freezeTime =System.currentTimeMillis() + env.config.pointFreezeMillis;
            while(System.currentTimeMillis()<freezeTime){
                env.ui.setFreeze(id, freezeTime - System.currentTimeMillis());
                Thread.sleep(Math.min(SLEEPING_TIME, Math.max(freezeTime - System.currentTimeMillis(),1)));
            }
        } catch(InterruptedException e){}
   
        env.ui.setFreeze(this.id , STOP_SHOW); 
        //Updating the flag.
        flag = 0;
    }

    /**
     * Penalize a player and perform other related actions.
     * @post - the player's score doesnt change.
     * @post - the flag of the player that determines what the player need to do is reset to 0.
     */
    public void penalty() {
        final int STOP_SHOW = 0;
        final int SLEEPING_TIME = 950; 
        try{
            env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
            long freezeTime= System.currentTimeMillis() + env.config.penaltyFreezeMillis;
            while(System.currentTimeMillis()< freezeTime){
                env.ui.setFreeze(id, freezeTime - System.currentTimeMillis());
                Thread.sleep(Math.min(SLEEPING_TIME, Math.max(freezeTime - System.currentTimeMillis(),1)));
            }
        } catch(InterruptedException e){}
        
        env.ui.setFreeze(this.id ,STOP_SHOW);
        //Updating the flag.
        flag = 0;
    }

    public int score() {
        return score;
    }

    public int[] setListToArray(List<Integer> list){
        int[] arr = new int[env.config.featureSize];
        for(int i = 0; i < list.size(); i++){
            arr[i] = list.get(i);
        }
        return arr;
    }


    public CopyOnWriteArrayList<Integer> getTokenList(){
        return tokensList;
    }


    public boolean removeToken(int slot){
        synchronized(table){
            if(tokensList.contains(slot) && table.slotToCard[slot] != null){
                tokensList.remove((Integer)slot);
                table.removeToken(this.id, slot);
                return true;
            }
            return false;
        }
    }

    public boolean placeToken(int slot){
        synchronized(table){
            if(tokensList.size() < env.config.featureSize && table.slotToCard[slot]!=null){
                tokensList.add(slot);
                table.placeToken(this.id, slot);
                return true;
            }
            return false;
        }
    }

    public boolean isHuman(){
        return human;        
    }

    public void setPlayerThread(Thread playerThread){
        this.playerThread = playerThread;
    }

    public Thread getPlayerThread (){
        return playerThread;
    }

    public void setFlag(int newFlag){
        this.flag = newFlag;
    }

}
