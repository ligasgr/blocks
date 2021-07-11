package blocks.secrets.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class SecretsConfig {
    private final Config config;
    private final String secretsConfigFile;

    public SecretsConfig(final String env) {
        this.secretsConfigFile = env + "-secrets.conf";
        this.config = ConfigFactory.parseResourcesAnySyntax(secretsConfigFile).resolve();
        EncryptionUtil.encrypt("sample");
    }

    public String getSecret(String key) {
        if (!this.config.hasPath(key)) {
            throw new IllegalStateException(String.format("No value provided for secret %s in %s", key, secretsConfigFile));
        }
        return EncryptionUtil.decrypt(this.config.getString(key));
    }
}
