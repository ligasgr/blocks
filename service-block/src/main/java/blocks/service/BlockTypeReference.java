package blocks.service;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class BlockTypeReference<T> implements Comparable<BlockTypeReference<T>>
{
    protected final Type _type;

    protected BlockTypeReference()
    {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof Class<?>) { // sanity check, should never happen
            throw new IllegalArgumentException("Internal error: TypeReference constructed without actual type information");
        }
        _type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }

    public Type getType() { return _type; }

    /**
     * The only reason we define this method (and require implementation
     * of <code>Comparable</code>) is to prevent constructing a
     * reference without type information.
     */
    @Override
    public int compareTo(BlockTypeReference<T> o) { return 0; }
    // just need an implementation, not a good one... hence ^^^
}
