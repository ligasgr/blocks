package blocks.health;

import akka.actor.typed.ActorRef;
import akka.http.javadsl.server.Route;
import blocks.service.AbstractBlock;
import blocks.service.Block;
import blocks.service.BlockContext;
import blocks.service.BlockRef;
import blocks.service.BlockStatus;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class HealthBlock extends AbstractBlock<ActorRef<HealthProtocol.Message>> {
    private Queue<BlockRef<?>> initialisationUpdatesQueue = new LinkedList<>();
    private Logger log;
    private HealthRestService healthRestService;
    private Map<BlockRef<?>, Block<?>> blocks;

    @Override
    public boolean isMandatory() {
        return true;
    }

    @Override
    protected CompletableFuture<ActorRef<HealthProtocol.Message>> getBlockOutputFuture(final BlockContext blockContext) {
        this.log = blockContext.context.getLog();
        this.log.info("Initializing HealthBlock");
        ActorRef<HealthProtocol.Message> healthActor = blockContext.context.spawn(HealthActor.behavior(blockContext.startInstant, blockContext.clock), "healthActor");
        healthRestService = new HealthRestService(healthActor, blockContext.context.getSystem().scheduler());
        for (Map.Entry<BlockRef<?>, Block<?>> block : blocks.entrySet()) {
            healthActor.tell(new HealthProtocol.RegisterBlock(block.getKey().key, block.getValue().isMandatory()));
        }
        for (BlockRef<?> blockRef : initialisationUpdatesQueue) {
            healthActor.tell(new HealthProtocol.UpdateBlockStatus(blockRef.key, blocks.get(blockRef).getStatus()));
        }
        return CompletableFuture.completedFuture(healthActor);
    }

    @Override
    public Optional<Route> getCreatedRoutes() {
        return Optional.of(healthRestService.route());
    }

    @Override
    public Set<Class<?>> serviceClasses() {
        return Collections.singleton(HealthRestService.class);
    }

    @Override
    public void onInitializeBlocks(final Map<BlockRef<?>, Block<?>> blocks) {
        this.blocks = Collections.unmodifiableMap(blocks);
    }

    @Override
    public void onInitializeBlock(final BlockRef<?> blockRef) {
        enqueueOrSendUpdateForKey(blockRef);
    }

    @Override
    public void onInitializedBlock(final BlockRef<?> blockRef) {
        enqueueOrSendUpdateForKey(blockRef);
    }

    private void enqueueOrSendUpdateForKey(final BlockRef<?> blockRef) {
        if (getStatus() == BlockStatus.NOT_INITIALIZED || getStatus() == BlockStatus.INITIALIZING) {
            initialisationUpdatesQueue.add(blockRef);
        }
        if (getStatus() == BlockStatus.INITIALIZED) {
            this.outputFuture.thenAcceptAsync(ref -> ref.tell(new HealthProtocol.UpdateBlockStatus(blockRef.key, blocks.get(blockRef).getStatus())));
        }
    }
}
