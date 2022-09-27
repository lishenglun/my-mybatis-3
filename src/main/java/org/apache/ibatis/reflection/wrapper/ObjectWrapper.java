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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 对象包装器
 *
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  // 获取值
  // 获取对应prop，在当前类中属性对象
  Object get(PropertyTokenizer prop);

  // 设置值
  // 给对应prop，在当前类中属性对象赋值
  void set(PropertyTokenizer prop, Object value);

  // 查找对应属性的属性值
  String findProperty(String name, boolean useCamelCaseMapping/* 是否使用驼峰映射 */);

  // 获取类中所有get方法名
  String[] getGetterNames();

  // 获取类中所有set方法名
  String[] getSetterNames();

  // 获取对应名称的set方法的参数类型
  Class<?> getSetterType(String name);

  // 获取对应名称的get方法的参数类型
  Class<?> getGetterType(String name);

  // 是否有对应名称的set方法
  // 例如：hasSetter(age)，就是看是否有setAge()
  boolean hasSetter(String name);

  // 是否有对应名称的get方法
  // 例如：hasGetter(age)，就是看是否有getAge()
  boolean hasGetter(String name);

  // 实例化属性对象
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  // 是否是集合
  boolean isCollection();

  // 添加属性
  void add(Object element);

  // 添加属性
  <E> void addAll(List<E> element);

}
