package blocks.health;

import blocks.service.BlockStatus;

public class BlockHealthInfo {
    public final BlockStatus status;
    public final boolean mandatory;

    public BlockHealthInfo(final BlockStatus status, final boolean mandatory) {
        this.status = status;
        this.mandatory = mandatory;
    }
}
