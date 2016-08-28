package com.jfireframework.mvc.newbinder.field.array;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import com.jfireframework.mvc.newbinder.node.ParamNode;
import com.jfireframework.mvc.newbinder.node.StringValueNode;

public class ArrayWIntegerField extends AbstractArrayField
{
    
    public ArrayWIntegerField(Field field)
    {
        super(field);
    }
    
    @Override
    protected Object buildFromArray(int size, List<String> values)
    {
        Integer[] array = new Integer[size];
        int index = 0;
        for (String each : values)
        {
            array[index] = Integer.valueOf(each);
            index += 1;
        }
        return array;
    }
    
    @Override
    protected Object buildFromTree(int size, Set<Entry<String, ParamNode>> set)
    {
        Integer[] array = new Integer[size];
        for (Entry<String, ParamNode> each : set)
        {
            int tmp = Integer.valueOf(each.getKey());
            array[tmp] = Integer.valueOf(((StringValueNode) each.getValue()).getValue());
        }
        return array;
    }
    
}
