package blocks.service;

import java.time.Duration;
import java.util.List;

public interface BlockConfig {
    boolean hasPath(String path);

    String getString(String path);

    int getInt(String path);

    Duration getDuration(String path);

    List<String> getStringList(final String path);
}
