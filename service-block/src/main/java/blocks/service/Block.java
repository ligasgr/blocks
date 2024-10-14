package blocks.service;

import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.server.Route;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public interface Block<T> {
    boolean isMandatory();

    CompletionStage<T> initialize(final BlockContext context);

    Throwable failureInfo();

    Optional<T> getBlockOutput();

    BlockStatus getStatus();

    default Optional<Route> getCreatedRoutes() {
        return Optional.empty();
    }

    default Set<Class<?>> serviceClasses() {
        return Collections.emptySet();
    }

    default List<ServerBinding> serverBindings() {
        return Collections.emptyList();
    }

    default void onInitializeBlocks(Map<BlockRef<?>, Block<?>> blocks) {

    }

    default void onInitializeBlock(BlockRef<?> blockRef) {

    }

    default void onInitializedBlock(BlockRef<?> blockRef) {

    }

    BlockRef<T> ref();
}
