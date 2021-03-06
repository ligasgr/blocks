package blocks.testkit;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Props;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public abstract class BlockTestBase {

    protected static ActorTestKit actorTestKit;

    @BeforeClass
    public static void beforeAll() {
        actorTestKit = ActorTestKit.create("akkaBlockTest-" + System.nanoTime());
    }

    @AfterClass
    public static void afterAll() {
        actorTestKit.shutdownTestKit();
    }

    protected static <T> ActorRef<T> spawnActor(final Behavior<T> behavior, final String actorNamePrefix) {
        return actorTestKit.spawn(behavior, actorNamePrefix + '-' + System.nanoTime(), Props.empty());
    }
}
