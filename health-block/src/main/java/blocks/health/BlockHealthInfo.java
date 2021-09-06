package blocks.health;

import blocks.service.BlockStatus;

import java.util.Objects;

public final class BlockHealthInfo {
    public final BlockStatus status;
    public final boolean mandatory;

    public BlockHealthInfo(final BlockStatus status, final boolean mandatory) {
        this.status = status;
        this.mandatory = mandatory;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockHealthInfo that = (BlockHealthInfo) o;
        return mandatory == that.mandatory && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, mandatory);
    }

    @Override
    public String toString() {
        return "BlockHealthInfo{" +
            "status=" + status +
            ", mandatory=" + mandatory +
            '}';
    }
}
