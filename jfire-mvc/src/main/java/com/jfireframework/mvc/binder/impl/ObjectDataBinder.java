package com.jfireframework.mvc.binder.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.jfireframework.baseutil.exception.JustThrowException;
import com.jfireframework.baseutil.reflect.ReflectUtil;
import com.jfireframework.baseutil.simplelog.ConsoleLogFactory;
import com.jfireframework.baseutil.simplelog.Logger;
import com.jfireframework.mvc.annotation.MvcIgnore;
import com.jfireframework.mvc.binder.DataBinder;
import com.jfireframework.mvc.binder.field.AbstractBinderField;
import com.jfireframework.mvc.binder.field.BinderField;
import com.jfireframework.mvc.binder.node.ParamNode;
import com.jfireframework.mvc.binder.node.TreeValueNode;

public class ObjectDataBinder implements DataBinder
{
    private final String        prefixName;
    private final BinderField[] fields;
    private final Class<?>      ckass;
    private final static Logger LOGGER = ConsoleLogFactory.getLogger();
    
    public ObjectDataBinder(Class<?> ckass, String prefixName, Annotation[] annotations)
    {
        this.ckass = ckass;
        this.prefixName = prefixName;
        Field[] t_fFields = ReflectUtil.getAllFields(ckass);
        LinkedList<BinderField> list = new LinkedList<BinderField>();
        for (Field each : t_fFields)
        {
            if (Modifier.isStatic(each.getModifiers()) || Modifier.isFinal(each.getModifiers()) || each.isAnnotationPresent(MvcIgnore.class))
            {
                continue;
            }
            list.add(AbstractBinderField.build(each));
        }
        fields = list.toArray(new BinderField[list.size()]);
    }
    
    @Override
    public Object bind(HttpServletRequest request, TreeValueNode treeValueNode, HttpServletResponse response)
    {
        if (prefixName.length() != 0)
        {
            treeValueNode = (TreeValueNode) treeValueNode.get(prefixName);
        }
        try
        {
            if (treeValueNode == null)
            {
                LOGGER.debug("尝试获取对象{}的数据，但是不存在", prefixName);
                return null;
            }
            Object entity = null;
            for (BinderField each : fields)
            {
                ParamNode node = treeValueNode.get(each.getName());
                if (node != null)
                {
                    if (entity == null)
                    {
                        entity = ckass.newInstance();
                    }
                    each.setValue(request, response, node, entity);
                }
            }
            return entity;
        }
        catch (Exception e)
        {
            throw new JustThrowException(e);
        }
        
    }
    
    @Override
    public String getParamName()
    {
        return prefixName;
    }
    
}
