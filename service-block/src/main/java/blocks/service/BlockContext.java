package blocks.service;

import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.server.Route;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class BlockContext {
    public final ActorContext<?> context;
    public final Http http;
    public final Route route;
    public final String env;
    public final ServiceConfig config;
    public final Instant startInstant;
    public final Clock clock;
    public final Set<BlockRef<?>> blockRefs;
    private final Function<BlockRef<?>, Block<?>> blockFetchFunction;

    public BlockContext(final ActorContext<?> context, final Http http, final Route route, final String env, final ServiceConfig config, final Instant startInstant, final Clock clock, final Set<BlockRef<?>> blockRefs, Function<BlockRef<?>, Block<?>> blockFetchFunction) {
        this.context = context;
        this.http = http;
        this.route = route;
        this.env = env;
        this.config = config;
        this.startInstant = startInstant;
        this.clock = clock;
        this.blockRefs = blockRefs;
        this.blockFetchFunction = blockFetchFunction;
    }

    @SuppressWarnings("unchecked")
    public <B> Block<B> getBlock(BlockRef<B> key) {
        return (Block<B>) blockFetchFunction.apply(key);
    }

    @SuppressWarnings("unchecked")
    public <B> B getBlockOutput(BlockRef<B> key) {
        Block<?> block = blockFetchFunction.apply(key);
        if (block == null) {
            throw new IllegalStateException("Missing block " + key.key + " (" + key.type.getType() + "). Have you forgotten to add the block instance to list of blocks?");
        }
        Optional<B> blockOutput = (Optional<B>) block.getBlockOutput();
        if (blockOutput.isEmpty()) {
            throw new IllegalStateException("Missing output of " + key.key + " " + key.type.getType());
        }
        return blockOutput.get();
    }
}
