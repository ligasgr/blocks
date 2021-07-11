package blocks.storage.file;

import akka.stream.IOResult;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

import java.util.concurrent.CompletionStage;

public interface Storage {
    Sink<ByteString, CompletionStage<IOResult>> toPath(final String path);

    Source<ByteString, CompletionStage<IOResult>> fromPath(final String path);

    boolean hasPath(String path);
}
