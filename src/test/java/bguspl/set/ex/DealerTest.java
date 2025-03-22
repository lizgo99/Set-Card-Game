package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class DealerTest {

    Dealer dealer;
    @Mock
    Env env;
    @Mock
    Table table;
    @Mock
    private UserInterface ui;
    @Mock
    Util util;
    @Mock
    private Logger logger;
    @Mock
    private List<Integer> remainingCards;

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        Player[] players = new Player[env.config.players];
        dealer = new Dealer(env, table, players);
    }

    void tearDown() {
        
    }

    @Test
    void addPlayerToCheck(){
        int expectedPlayersToCheckSize = dealer.playersToCheck.size() + 1;
        dealer.addPlayerToCheck(new Player(env, dealer, table, 1, true));
        assertEquals(expectedPlayersToCheckSize, dealer.playersToCheck.size());
    }

    
    @Test
    void shouldFinish(){
        dealer.terminate = true;
        assertEquals(dealer.terminate, dealer.shouldFinish());
    }
    



}