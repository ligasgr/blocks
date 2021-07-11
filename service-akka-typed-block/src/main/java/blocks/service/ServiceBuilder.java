package blocks.service;

import akka.actor.typed.ActorSystem;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;

public class ServiceBuilder {
    final Map<BlockRef<?>, Block<?>> blocks = new HashMap<>();
    final Map<BlockRef<?>, Set<BlockRef<?>>> blockDependencies = new HashMap<>();

    private ServiceBuilder() {

    }

    public static ServiceBuilder newService() {
        return new ServiceBuilder();
    }

    public <T> ServiceBuilder withBlock(final BlockRef<T> blockRef, final Block<T> block, final BlockRef<?>... dependencies) {
        blocks.put(blockRef, block);
        blockDependencies.put(blockRef, new HashSet<>(asList(dependencies)));
        return this;
    }

    public ActorSystem<ServiceProtocol.Message> start(final Clock clock, final ServiceConfig config) {
        return ActorSystem.create(ServiceActor.behavior(clock, config, blocks, blockDependencies), "service");
    }
}
