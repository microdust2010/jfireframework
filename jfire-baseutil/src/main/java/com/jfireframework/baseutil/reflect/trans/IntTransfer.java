package com.jfireframework.baseutil.reflect.trans;

public class IntTransfer implements Transfer
{
    
    @Override
    public Object trans(String value)
    {
        return Integer.valueOf(value);
    }
    
}
