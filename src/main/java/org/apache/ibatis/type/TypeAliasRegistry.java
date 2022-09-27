/*
 *    Copyright 2009-2022 the original author or authors.
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
package org.apache.ibatis.type;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;

/**
 * 类型别名注册器
 *
 * @author Clinton Begin
 */
public class TypeAliasRegistry {

  /**
   * 题外：在实例化{@link Configuration}的时候，会注册一批别名
   */
  // "类型别名"集合
  // key：别名
  // value：别名对应的Class
  // 例如：JDBC = JdbcTransactionFactory
  private final Map<String, Class<?>> typeAliases = new HashMap<>();

  public TypeAliasRegistry() {
    //构造函数里注册系统内置的类型别名
    registerAlias("string", String.class);

    //基本包装类型
    registerAlias("byte", Byte.class);
    registerAlias("char", Character.class);
    registerAlias("character", Character.class);
    registerAlias("long", Long.class);
    registerAlias("short", Short.class);
    registerAlias("int", Integer.class);
    registerAlias("integer", Integer.class);
    registerAlias("double", Double.class);
    registerAlias("float", Float.class);
    registerAlias("boolean", Boolean.class);

    //基本数组包装类型
    registerAlias("byte[]", Byte[].class);
    registerAlias("char[]", Character[].class);
    registerAlias("character[]", Character[].class);
    registerAlias("long[]", Long[].class);
    registerAlias("short[]", Short[].class);
    registerAlias("int[]", Integer[].class);
    registerAlias("integer[]", Integer[].class);
    registerAlias("double[]", Double[].class);
    registerAlias("float[]", Float[].class);
    registerAlias("boolean[]", Boolean[].class);

    //加个下划线，就变成了基本类型
    registerAlias("_byte", byte.class);
    registerAlias("_char", char.class);
    registerAlias("_character", char.class);
    registerAlias("_long", long.class);
    registerAlias("_short", short.class);
    registerAlias("_int", int.class);
    registerAlias("_integer", int.class);
    registerAlias("_double", double.class);
    registerAlias("_float", float.class);
    registerAlias("_boolean", boolean.class);

    //加个下划线，就变成了基本数组类型
    registerAlias("_byte[]", byte[].class);
    registerAlias("_char[]", char[].class);
    registerAlias("_character[]", char[].class);
    registerAlias("_long[]", long[].class);
    registerAlias("_short[]", short[].class);
    registerAlias("_int[]", int[].class);
    registerAlias("_integer[]", int[].class);
    registerAlias("_double[]", double[].class);
    registerAlias("_float[]", float[].class);
    registerAlias("_boolean[]", boolean[].class);

    //日期数字型
    registerAlias("date", Date.class);
    registerAlias("decimal", BigDecimal.class);
    registerAlias("bigdecimal", BigDecimal.class);
    registerAlias("biginteger", BigInteger.class);
    registerAlias("object", Object.class);

    registerAlias("date[]", Date[].class);
    registerAlias("decimal[]", BigDecimal[].class);
    registerAlias("bigdecimal[]", BigDecimal[].class);
    registerAlias("biginteger[]", BigInteger[].class);
    registerAlias("object[]", Object[].class);

    //集合型
    registerAlias("map", Map.class);
    registerAlias("hashmap", HashMap.class);
    registerAlias("list", List.class);
    registerAlias("arraylist", ArrayList.class);
    registerAlias("collection", Collection.class);
    registerAlias("iterator", Iterator.class);

    //还有个ResultSet型
    registerAlias("ResultSet", ResultSet.class);
  }


  /**
   * 解析类型别名：
   * （1）如果类型别名存在于类型别名当中，则通过类型别名，从类型别名集合当中，获取到对应的Class
   * （2）如果不存在，则把别名作为"类的全限定名"，然后获取其对应的Class
   *
   * @param string    类型别名
   * @param <T>
   * @return
   */
  @SuppressWarnings("unchecked")
  // throws class cast exception as well if types cannot be assigned —— 如果无法分配类型，也会抛出类转换异常
  public <T> Class<T> resolveAlias(String string) {
    try {
      /* 1、类型别名为null，则返回null */
      if (string == null) {
        return null;
      }

      /* 2、将类型别名转换为小写 */
      // issue #748
      // 先转成小写再解析
      // 这里转个小写也有bug？见748号bug(在google code上)
      // https://code.google.com/p/mybatis/issues
      // 比如如果本地语言是Turkish，那i转成大写就不是I了，而是另外一个字符（İ）。这样土耳其的机器就用不了mybatis了！这是一个很大的bug，但是基本上每个人都会犯......
      String key = string.toLowerCase(Locale.ENGLISH);

      /* 3、判断类型别名集合中，是否存在当前类型别名。存在，则从类型别名集合当中，获取到对应的类型Class */
      Class<T> value;
      // 原理就很简单了，从HashMap里找对应的键值，找到则返回类型别名对应的Class
      if (typeAliases.containsKey(key)) {
        value = (Class<T>) typeAliases.get(key);
      }
      /* 4、不存在，就直接将别名作为"类的全限定名"，然后获取其对应的Class */
      // 找不到，再试着将String直接转成Class(这样怪不得我们也可以直接用java.lang.Integer的方式定义，也可以就int这么定义)
      else {
        value = (Class<T>) Resources.classForName(string);
      }
      return value;
    } catch (ClassNotFoundException e) {
      throw new TypeException("Could not resolve type alias '" + string + "'.  Cause: " + e, e);
    }
  }

  public void registerAliases(String packageName) {
    registerAliases(packageName, Object.class);
  }

  //扫描并注册包下所有继承于superType的类型别名
  public void registerAliases(String packageName, Class<?> superType) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    Set<Class<? extends Class<?>>> typeSet = resolverUtil.getClasses();
    for (Class<?> type : typeSet) {
      // Ignore inner classes and interfaces (including package-info.java)
      // Skip also inner classes. See issue #6
      if (!type.isAnonymousClass() && !type.isInterface() && !type.isMemberClass()) {
        registerAlias(type);
      }
    }
  }

  //注册类型别名
  public void registerAlias(Class<?> type) {
    //如果没有类型别名，用Class.getSimpleName来注册
    String alias = type.getSimpleName();
    //或者通过Alias注解来注册(Class.getAnnotation)
    Alias aliasAnnotation = type.getAnnotation(Alias.class);
    if (aliasAnnotation != null) {
      alias = aliasAnnotation.value();
    }
    registerAlias(alias, type);
  }

  /**
   * 注册类型别名和对应的Class
   *
   * @param alias 别名
   * @param value 别名对应的Class
   */
  public void registerAlias(String alias, Class<?> value) {
    if (alias == null) {
      throw new TypeException("The parameter alias cannot be null");
    }
    /* 1、将别名中的英文变为小写 */
    // issue #748
    String key = alias.toLowerCase(Locale.ENGLISH);

    /* 2、如果已经存在该别名，但是存在的"别名类型"和目前的"别名类型"不一致，则报错 */
    if (typeAliases.containsKey(key) && typeAliases.get(key) != null && !typeAliases.get(key).equals(value)) {
      throw new TypeException("The alias '" + alias + "' is already mapped to the value '" + typeAliases.get(key).getName() + "'.");
    }

    /* 3、存放别名和对应的类型，到"类型别名"集合当中 */
    typeAliases.put(key, value);
  }

  public void registerAlias(String alias, String value) {
    try {
      registerAlias(alias, Resources.classForName(value));
    } catch (ClassNotFoundException e) {
      throw new TypeException("Error registering type alias " + alias + " for " + value + ". Cause: " + e, e);
    }
  }

  /**
   * Gets the type aliases.
   *
   * @return the type aliases
   * @since 3.2.2
   */
  public Map<String, Class<?>> getTypeAliases() {
    return Collections.unmodifiableMap(typeAliases);
  }

}
