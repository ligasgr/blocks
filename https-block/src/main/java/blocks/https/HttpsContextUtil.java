package blocks.https;

import org.apache.pekko.http.javadsl.ConnectionContext;
import org.apache.pekko.http.javadsl.HttpsConnectionContext;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;

public class HttpsContextUtil {
    public static HttpsConnectionContext createHttpsContext(final KeyStore keyStore, final char[] privateKeyPassword) {
        try {
            final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(keyStore, privateKeyPassword);
            final TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(keyStore);
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            return ConnectionContext.httpsServer(sslContext);
        } catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalArgumentException("Failed to initialize https context", e);
        }
    }
}