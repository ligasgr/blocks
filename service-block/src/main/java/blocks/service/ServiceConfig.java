package blocks.service;

import java.util.Map;
import java.util.Optional;

public interface ServiceConfig {
    String getEnv();

    String getHost();

    Optional<Integer> getHttpPort();

    Optional<Integer> getHttpsPort();

    String getString(String path);

    int getInt(String path);

    Map<String, String> getStringMap(String path);

    BlockConfig getBlockConfig(String path);
}
