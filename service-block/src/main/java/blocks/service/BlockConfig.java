package blocks.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface BlockConfig {
    boolean hasPath(String path);

    String getString(String path);

    int getInt(String path);

    Duration getDuration(String path);

    List<String> getStringList(final String path);

    Map<String, String> getStringMap(String path);

    boolean getBoolean(String path);
}
