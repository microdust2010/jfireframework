package com.jfireframework.baseutil.collection.buffer;

public class CacheSize
{
    private int size;
    private int index;
    
    public CacheSize(int size, int index)
    {
        this.index = index;
        this.size = size;
    }
    
    public int index()
    {
        return index;
    }
    
    public boolean biggerThan(int size)
    {
        return this.size > size;
    }
    
    public int size()
    {
        return size;
    }
}
