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
package org.apache.ibatis.reflection.factory;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.Reflector;

/**
 * @author Clinton Begin
 */
public class DefaultObjectFactory implements ObjectFactory, Serializable {

  private static final long serialVersionUID = -8855120656740914948L;

  /**
   * 使用默认的无参构造器，实例化对象
   *
   * @param type
   *          Object type
   * @param <T>
   * @return
   */
  @Override
  public <T> T create(Class<T> type) {
    return create(type, null, null);
  }

  /**
   * 使用有参构造器（构造器参数类型和参数值），实例化对象
   *
   * @param type                          类型
   * @param constructorArgTypes           构造器参数类型
   * @param constructorArgs               构造器参数值
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes/* 构造器参数类型 */, List<Object> constructorArgs/* 构造器参数值 */) {

    /* 1、类型 */
    Class<?> classToCreate = resolveInterface(type);

    /* 2、根据构造器参数类型和参数值，实例化对象 */
    // we know types are assignable —— 我们知道类型是可分配的
    return (T) instantiateClass(classToCreate, constructorArgTypes, constructorArgs);

  }

  /**
   * 实例化对象：
   * (1)构造参数类型，或者构造器参数为空，则获取默认的构造器；然后通过默认的构造器实例化对象
   * (2)通过构造器参数类型，获取对应的构造器；然后用获取到的构造器，和参数值，实例化对象
   *
   * @param type
   * @param constructorArgTypes     构造参数类型
   * @param constructorArgs         构造参数值
   * @param <T>
   * @return
   */
  private  <T> T instantiateClass(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    try {
      // 构造器
      Constructor<T> constructor;

      /* 1、构造参数类型，或者构造器参数为空，则获取默认的构造器；然后通过默认的构造器实例化对象 */

      if (constructorArgTypes == null || constructorArgs == null) {
        // 获取默认的构造器
        constructor = type.getDeclaredConstructor();
        try {
          // 通过默认的构造器实例化对象
          return constructor.newInstance();
        } catch (IllegalAccessException e) {
          if (Reflector.canControlMemberAccessible()) {
            constructor.setAccessible(true);
            return constructor.newInstance();
          } else {
            throw e;
          }
        }
      }

      /* 2、通过构造器参数类型，获取对应的构造器；然后用获取到的构造器，和参数值，实例化对象 */

      // 通过构造器参数类型，获取对应的构造器
      constructor = type.getDeclaredConstructor(constructorArgTypes.toArray(new Class[0]));
      try {
        // 通过构造器和构造器参数值，实例化对象
        return constructor.newInstance(constructorArgs.toArray(new Object[0]));
      } catch (IllegalAccessException e) {
        if (Reflector.canControlMemberAccessible()) {
          constructor.setAccessible(true);
          return constructor.newInstance(constructorArgs.toArray(new Object[0]));
        } else {
          throw e;
        }
      }
    } catch (Exception e) {
      String argTypes = Optional.ofNullable(constructorArgTypes).orElseGet(Collections::emptyList)
          .stream().map(Class::getSimpleName).collect(Collectors.joining(","));
      String argValues = Optional.ofNullable(constructorArgs).orElseGet(Collections::emptyList)
          .stream().map(String::valueOf).collect(Collectors.joining(","));
      throw new ReflectionException("Error instantiating " + type + " with invalid types (" + argTypes + ") or values (" + argValues + "). Cause: " + e, e);
    }
  }

  /**
   * 如果type是指定接口，则为其赋予默认的实现类；否则返回type
   */
  protected Class<?> resolveInterface(Class<?> type) {
    Class<?> classToCreate;
    /* 1、type = List/Collection/Iterable接口，则指定type对应的实现类为ArrayList */
    if (type == List.class || type == Collection.class || type == Iterable.class) {
      classToCreate = ArrayList.class;
    }
    /* 2、type = Map接口，则指定type对应的实现类为HashMap */
    else if (type == Map.class) {
      classToCreate = HashMap.class;
    }
    /* 3、type = SortedSet接口，则指定type对应的实现类为TreeSet */
    else if (type == SortedSet.class) { // issue #510 Collections Support
      classToCreate = TreeSet.class;
    }
    /* 4、type = Set接口，则指定type对应的实现类为HashSet */
    else if (type == Set.class) {
      classToCreate = HashSet.class;
    }
    /* 5、不是上诉情况，则type作为实现类 */
    else {
      classToCreate = type;
    }
    return classToCreate;
  }

  @Override
  public <T> boolean isCollection(Class<T> type) {
    return Collection.class.isAssignableFrom(type);
  }

}
