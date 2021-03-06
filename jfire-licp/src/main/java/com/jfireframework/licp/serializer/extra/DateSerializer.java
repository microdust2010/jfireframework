package com.jfireframework.licp.serializer.extra;

import java.nio.ByteBuffer;
import java.util.Date;
import com.jfireframework.baseutil.collection.buffer.ByteBuf;
import com.jfireframework.licp.Licp;
import com.jfireframework.licp.serializer.LicpSerializer;
import com.jfireframework.licp.util.BufferUtil;

public class DateSerializer implements LicpSerializer
{
    private final boolean sqlDate;
    
    public DateSerializer(boolean sqlDate)
    {
        this.sqlDate = sqlDate;
    }
    
    @Override
    public void serialize(Object src, ByteBuf<?> buf, Licp licp)
    {
        if (sqlDate)
        {
            java.sql.Date date = (java.sql.Date) src;
            buf.writeVarLong(date.getTime());
        }
        else
        {
            Date date = (Date) src;
            buf.writeVarLong(date.getTime());
        }
    }
    
    @Override
    public Object deserialize(ByteBuf<?> buf, Licp licp)
    {
        long time = buf.readVarLong();
        if (sqlDate)
        {
            Object result = new java.sql.Date(time);
            licp.putObject(result);
            return result;
        }
        else
        {
            Object result = new Date(time);
            licp.putObject(result);
            return result;
        }
    }
    
    @Override
    public Object deserialize(ByteBuffer buf, Licp licp)
    {
        long time = BufferUtil.readVarLong(buf);
        if (sqlDate)
        {
            Object result = new java.sql.Date(time);
            licp.putObject(result);
            return result;
        }
        else
        {
            Object result = new Date(time);
            licp.putObject(result);
            return result;
        }
    }
    
}
