package blocks.service;

import com.typesafe.config.Config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ServiceConfig {
    String getEnv();

    String getHost();

    Optional<Integer> getHttpPort();

    Optional<Integer> getHttpsPort();

    String getString(String path);

    int getInt(String path);

    Duration getDuration(String path);

    List<String> getStringList(final String path);

    Map<String, String> getStringMap(String path);

    boolean getBoolean(String path);

    boolean hasPath(String path);

    BlockConfig getBlockConfig(String path);

    Config asTypesafeConfig();
}
