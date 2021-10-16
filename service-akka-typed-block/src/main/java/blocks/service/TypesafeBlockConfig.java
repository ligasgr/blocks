package blocks.service;

import com.typesafe.config.Config;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TypesafeBlockConfig implements BlockConfig {
    private final Config config;

    public TypesafeBlockConfig(final Config config) {
        this.config = config;
    }

    @Override
    public boolean hasPath(final String path) {
        return config.hasPath(path);
    }

    @Override
    public String getString(final String path) {
        return config.getString(path);
    }

    @Override
    public int getInt(final String path) {
        return config.getInt(path);
    }

    @Override
    public Duration getDuration(final String path) {
        return config.getDuration(path);
    }

    @Override
    public List<String> getStringList(final String path) {
        return config.getStringList(path);
    }

    @Override
    public Map<String, String> getStringMap(String path) {
        final Config configConfig = this.config.getConfig(path);
        return Collections.unmodifiableMap(configConfig.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> configConfig.getString(e.getKey()))));
    }

    @Override
    public boolean getBoolean(final String path) {
        return config.getBoolean(path);
    }
}
