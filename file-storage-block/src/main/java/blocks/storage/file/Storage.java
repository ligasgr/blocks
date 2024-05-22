package blocks.storage.file;

import org.apache.pekko.stream.IOResult;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;

import java.util.concurrent.CompletionStage;

public interface Storage {
    Sink<ByteString, CompletionStage<IOResult>> toPath(final String path);

    Source<ByteString, CompletionStage<IOResult>> fromPath(final String path);

    boolean hasPath(String path);
}
