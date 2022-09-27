/*
 *    Copyright 2009-2021 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 通过Reflector和ReflectorFactory的组合使用，实现对复杂的属性表达式的解析
 *
 * @author Clinton Begin
 */
public class MetaClass/* 元类 */ {

  // ReflectorFactory对象用于缓存Reflector对象
  private final ReflectorFactory reflectorFactory;
  // 在创建MetaClass时会指定一个类，该Reflector对象用于记录该类相关的元信息
  private final Reflector reflector;

  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    // 创建Reflector对象
    this.reflector = reflectorFactory.findForClass(type);
  }

  // 使用静态方法创建MetaClass对象
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  public MetaClass metaClassForProperty(String name) {
    // 查找指定属性对应的class
    Class<?> propType = reflector.getGetterType(name);
    // 为该属性创建对应的MetaClass对象
    return MetaClass.forClass(propType, reflectorFactory);
  }

  public String findProperty(String name) {
    // 委托给buiLdProperty方法实现
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  /**
   * 获取类中所有的get方法名称，例如：User类中有getName()、getAge()，那么获取到的就是name、age
   */
  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  /**
   * 获取类中所有的set方法名称，例如：User类中有setName()、setAge()，那么获取到的就是name、age
   */
  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  public Class<?> getGetterType(String name) {
    // 解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 调用metaClassForProperty方法
      MetaClass metaProp = metaClassForProperty(prop);
      // 递归调用
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    // 调用getGetterType重载
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    // 获取表达式所表示的属性的类型
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  private Class<?> getGetterType(PropertyTokenizer prop) {
    // 获取属性类型
    Class<?> type = reflector.getGetterType(prop.getName());
    // 该表达式中是否使用[]指定了下标，且是Collection子类
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      // 通过TypeParameterResolver工具类解析属性的类型
      Type returnType = getGenericGetterType(prop.getName());
      // 针对ParameterizedType进行处理，即针对泛型集合类型进行处理
      if (returnType instanceof ParameterizedType) {
        // 获取实际的类型参数
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          // 泛型的类型
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  private Type getGenericGetterType(String propertyName) {
    try {
      // 根据RefLector.getMethods集合中记录的Invoker实现类的类型，决定解析getter方法
      Invoker invoker = reflector.getGetInvoker(propertyName);
      if (invoker instanceof MethodInvoker) {
        Field declaredMethod = MethodInvoker.class.getDeclaredField("method");
        declaredMethod.setAccessible(true);
        Method method = (Method) declaredMethod.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } else if (invoker instanceof GetFieldInvoker) {
        Field declaredField = GetFieldInvoker.class.getDeclaredField("field");
        declaredField.setAccessible(true);
        Field field = (Field) declaredField.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      // Ignored
    }
    return null;
  }

  /**
   * 判断是否有对应名称的set方法。
   * 例如hasSetter("name")，就是判断有没有setName()方法
   * 以及可以处理复杂的表达式，例如：hasSetter("user.name")，判断是否存在setName()方法
   */
  public boolean hasSetter(String name) {
    // 解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }


  /**
   * 判断是否有对应名称的get方法。
   * 例如hasGetter("name")，就是判断有没有getName()方法
   */
  public boolean hasGetter(String name) {
    // 解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 存在待处理的子表达式
    if (prop.hasNext()) {
      // PropertyTokenizer. name指定的属性有getter方法，才能处理子表达式
      if (reflector.hasGetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop);
        // 递归入口
        return metaProp.hasGetter(prop.getChildren());
      } else {
        // 递归出口
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  private StringBuilder buildProperty(String name, StringBuilder builder) {
    // 解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 是否有子表达式
    if (prop.hasNext()) {
      // 查找PropertyTokenizer.name对应的属性
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        // 追加属性名
        builder.append(propertyName);
        builder.append(".");
        // 为该属性创建对应的MetaClass对象
        MetaClass metaProp = metaClassForProperty(propertyName);
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
