package blocks.jms;

import blocks.service.BlockContext;

public interface CredentialsProvider {
    String getCredentials(final BlockContext blockContext);
}
