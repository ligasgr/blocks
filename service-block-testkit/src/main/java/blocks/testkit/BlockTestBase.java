package blocks.testkit;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Props;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class BlockTestBase {

    protected static ActorTestKit actorTestKit;

    @BeforeAll
    public static void beforeAll() {
        actorTestKit = ActorTestKit.create("akkaBlockTest-" + System.nanoTime());
    }

    @AfterAll
    public static void afterAll() {
        actorTestKit.shutdownTestKit();
    }

    protected static <T> ActorRef<T> spawnActor(final Behavior<T> behavior, final String actorNamePrefix) {
        return actorTestKit.spawn(behavior, actorNamePrefix + '-' + System.nanoTime(), Props.empty());
    }
}
