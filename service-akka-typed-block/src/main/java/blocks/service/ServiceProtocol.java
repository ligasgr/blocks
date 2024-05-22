package blocks.service;

import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.server.Route;

import java.util.List;

public abstract class ServiceProtocol {
    public interface Message {

    }

    static class InitializeBlocks implements Message {

    }

    static class InitializedBlock<T> implements Message {
        public final BlockRef<?> blockRef;
        public final T blockResult;
        public final Throwable t;

        public InitializedBlock(final BlockRef<?> blockRef, final T blockResult, final Throwable t) {
            this.blockRef = blockRef;
            this.blockResult = blockResult;
            this.t = t;
        }
    }

    static class InitializedAllBlocks implements Message {

    }

    static class BindPorts implements Message {
        public final Route route;

        public BindPorts(Route route) {
            this.route = route;
        }

    }

    static class PortsBound implements Message {
        public final List<ServerBinding> serverBindings;

        PortsBound(List<ServerBinding> serverBindings) {
            this.serverBindings = serverBindings;
        }

    }

    static class PortsFailedToBind implements Message {
        public final Throwable t;

        PortsFailedToBind(Throwable t) {
            this.t = t;
        }
    }
}
