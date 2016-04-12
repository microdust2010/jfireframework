package com.jfireframework.codejson.function.impl.write.wrapper;

import com.jfireframework.baseutil.collection.StringCache;
import com.jfireframework.codejson.function.impl.write.WriterAdapter;

public class ByteWriter extends WriterAdapter implements WrapperWriter
{
    
    @Override
    public void write(Object field, StringCache cache, Object entity)
    {
        cache.append((Byte) field);
    }
    
}
