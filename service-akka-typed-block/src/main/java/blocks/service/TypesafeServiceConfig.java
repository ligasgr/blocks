package blocks.service;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class TypesafeServiceConfig implements ServiceConfig {

    private final Config config;
    private final String env;

    public TypesafeServiceConfig() {
        Config envConfig = ConfigFactory.systemEnvironment()
                .withFallback(ConfigFactory.parseResourcesAnySyntax("env.conf"))
                .resolve();
        env = envConfig.getString("ENV");
        this.config = envConfig
                .withFallback(ConfigFactory.parseResourcesAnySyntax(env + ".conf"))
                .withFallback(ConfigFactory.defaultReference())
                .resolve();
    }

    @Override
    public String getEnv() {
        return env;
    }

    @Override
    public String getHost() {
        return config.getString("app.host");
    }

    @Override
    public Optional<Integer> getHttpPort() {
        return config.hasPath("app.httpPort") ? Optional.of(config.getInt("app.httpPort")) : Optional.empty();
    }

    @Override
    public Optional<Integer> getHttpsPort() {
        return config.hasPath("app.httpsPort") ? Optional.of(config.getInt("app.httpsPort")) : Optional.empty();
    }

    @Override
    public String getString(String path) {
        return config.getString(path);
    }

    @Override
    public int getInt(String path) {
        return config.getInt(path);
    }


    @Override
    public Map<String, String> getStringMap(String path) {
        return Collections.unmodifiableMap(config.getConfig(path).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> config.getString(e.getKey()))));
    }

    @Override
    public BlockConfig getBlockConfig(String path) {
        return new TypesafeBlockConfig(config.getConfig(path));
    }
}
