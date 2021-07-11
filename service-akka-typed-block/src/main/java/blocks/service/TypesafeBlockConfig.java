package blocks.service;

import com.typesafe.config.Config;

import java.time.Duration;
import java.util.List;

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
}
