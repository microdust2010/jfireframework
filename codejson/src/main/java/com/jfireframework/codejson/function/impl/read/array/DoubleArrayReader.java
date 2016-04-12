package com.jfireframework.codejson.function.impl.read.array;

import java.lang.reflect.Type;
import com.jfireframework.codejson.JsonArray;
import com.jfireframework.codejson.function.JsonReader;

public class DoubleArrayReader implements JsonReader
{
    @Override
    public Object read(Type entityType, Object value)
    {
        JsonArray jsonArray = (JsonArray) value;
        int size = jsonArray.size();
        double[] array = new double[size];
        for (int i = 0; i < size; i++)
        {
            array[i] = jsonArray.getDouble(i);
        }
        return array;
    }
}
