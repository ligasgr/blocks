package blocks.service;

import java.lang.reflect.Type;
import java.util.Objects;

public class BlockRef<T> implements Comparable<BlockTypeReference<T>> {
    public final String key;
    public final BlockTypeReference<T> type;

    public BlockRef(final String key, final Class<T> type) {
        this.key = key;
        this.type = new BlockTypeReference<>() {
            @Override
            public Type getType() {
                return type;
            }
        };
    }

    public BlockRef(final String key, final BlockTypeReference<T> type) {
        this.key = key;
        this.type = type;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final BlockRef<?> blockRef = (BlockRef<?>) o;
        return Objects.equals(key, blockRef.key) && Objects.equals(type, blockRef.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, type);
    }

    @Override
    public String toString() {
        return "BlockKey{" +
                "key='" + key + '\'' +
                ", type=" + type +
                '}';
    }
    
    @Override
    public int compareTo(BlockTypeReference<T> o) {
        return 0;
    }
}
