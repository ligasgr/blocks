package blocks.service;

import org.apache.pekko.japi.Pair;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class TypesafeServiceConfig implements ServiceConfig {

    public static Pair<String, Config> getDefaultEnvAndConfig() {
        final Config envConfig = ConfigFactory.systemEnvironment()
                .withFallback(ConfigFactory.parseResourcesAnySyntax("env.conf"))
                .resolve();
        final String env = envConfig.getString("ENV");
        final Config resolved = envConfig
                .withFallback(ConfigFactory.parseResourcesAnySyntax(env + ".conf"))
                .withFallback(ConfigFactory.defaultReference())
                .resolve();
        return Pair.create(env, resolved);
    }

    private final String env;
    private final Config config;

    public TypesafeServiceConfig() {
        this(getDefaultEnvAndConfig());
    }

    public TypesafeServiceConfig(final Pair<String, Config> envAndConfig) {
        this(envAndConfig.first(), envAndConfig.second());
    }

    public TypesafeServiceConfig(final String env, final Config config) {
        this.env = env;
        this.config = config;
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
    public Duration getDuration(String path) {
        return config.getDuration(path);
    }

    @Override
    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    @Override
    public Map<String, String> getStringMap(String path) {
        final Config configObject = this.config.getConfig(path);
        return Collections.unmodifiableMap(configObject.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> configObject.getString(e.getKey()))));
    }

    @Override
    public boolean getBoolean(final String path) {
        return config.getBoolean(path);
    }

    @Override
    public boolean hasPath(final String path) {
        return config.hasPath(path);
    }

    @Override
    public BlockConfig getBlockConfig(String path) {
        return new TypesafeBlockConfig(config.getConfig(path));
    }

    @Override
    public Config asTypesafeConfig() {
        return config;
    }
}
