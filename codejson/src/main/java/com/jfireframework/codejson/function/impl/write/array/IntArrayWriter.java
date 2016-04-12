package com.jfireframework.codejson.function.impl.write.array;

import com.jfireframework.baseutil.collection.StringCache;
import com.jfireframework.codejson.function.impl.write.WriterAdapter;

public class IntArrayWriter extends WriterAdapter
{
    
    @Override
    public void write(Object field, StringCache cache, Object entity)
    {
        int[] array = (int[]) field;
        cache.append('[');
        for (int each : array)
        {
            cache.append(each).append(',');
        }
        if (cache.isCommaLast())
        {
            cache.deleteLast();
        }
        cache.append(']');
    }
    
}
