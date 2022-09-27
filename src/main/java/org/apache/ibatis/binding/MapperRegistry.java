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
package org.apache.ibatis.binding;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

/**
 * 映射器注册器/Mapper注册器：里面包含了对Mapper的CRUD操作，方便操作mapper
 *
 *
 * 我们的mapper(配置文件/类)都是需要进行加载的，加载的时候就是通过MapperRegistry进行控制的
 *
 *
 *
 *
 * 最重要的是包含了knownMappers集合，存放了mapper接口与MapperProxyFactory的对应关系，MapperProxyFactory是用于生成mapper接口的代理对象
 *
 * <p>
 * 题外：什么是Mapper？Dao方法与sql的映射关系，就叫mapper。
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
public class MapperRegistry {

  // configuration对象，mybatis全局唯一的配置对象，其中包含了所有配置信息
  private final Configuration config;

  // 已知mapper集合（存放Mapper接口Class与对应MapperProxyFactory之间的关系，MapperProxyFactory里面也是只存放了"Mapper接口Class"）
  // key：Mapper接口Class
  // value：MapperProxyFactory，里面包含"Mapper接口Class"
  // >>> ⚠️MapperProxyFactory作用：用于创建mapper接口对应的代理对象！
  private final Map<Class<?>, MapperProxyFactory<?>> knownMappers/* 已知的映射器 *//* 我们已经知道的mapper有那些 */  = new HashMap<>();

  public MapperRegistry(Configuration config) {
    this.config = config;
  }



  /**
   * 获取mapper接口的动态代理对象（每次都是创建一个新的）：
   * 1、从configuration.mapperRegistry.knownMappers中，获取mapper接口对应的MapperProxyFactory（不存在就报错）
   * 2、根据MapperProxyFactory，创建当前mapper接口对应的动态代理对象（InvocationHandler是MapperProxy，里面包含：SqlSession,mapper接口Class,methodCache）
   *
   * @param type            mapper接口.class
   * @param sqlSession      DefaultSqlSession
   */
  @SuppressWarnings("unchecked")
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    /* 1、从configuration.mapperRegistry.knownMappers中，获取mapper接口对应的MapperProxyFactory（不存在就报错） */

    // 查找指定type对应MapperProxyFactory对象
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);

    // 如果mapperProxyFactory为空，则抛出异常
    if (mapperProxyFactory == null) {
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }

    /* 2、根据MapperProxyFactory，创建当前mapper接口对应的动态代理对象（InvocationHandler是MapperProxy，里面包含：SqlSession, mapper接口Class, methodCache） */

    try {
      /**
       * 在mybatis里面，我们写的所有的操作数据库的方法，都是在接口里面进行定义的。接口没有具体的实现子类，我是没法调用方法的！
       * 所以我要通过动态代理的方式来创建出来具体的代理对象，通过代理对象来完成具体方法的调用，而那个方法会映射到我们的sql语句里面去
       */
      // 创建实现了type接口的代理对象
      // 疑问：为什么不需要把type参数传入进去？因为MapperProxyFactory在创建之初，里面就包含了type
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }

  public <T> boolean hasMapper(Class<T> type) {
    return knownMappers.containsKey(type);
  }

  /**
   * 注册mapper（单个）：
   * 会判断，必须是接口，才会进行加载和解析
   * 1、先是构建mapper接口对应的MapperProxyFactory，用于生成mapper接口的代理对象；并将它两的对应关系，存入knownMappers（已知mapper）集合中
   * 2、然后去解析mapper，分为2部分
   * （1）先解析mapper接口对应的dao.xml文件，将对应信息放入Configuration；—— 配置文件开发
   * （2）然后再解析mapper接口（把mapper接口作为映射文件进行解析），将对应信息放入Configuration—— 注解开发
   */
  public <T> void addMapper(Class<T> type) {
    /* 1、判断mapper Class是不是接口。是接口，才会添加；否则不做任何事情，忽略该mapper Class */
    // 注意：⚠️也就是说，mapper必须是接口！才会添加
    if (type.isInterface()) {

      // 检测是否己经添加过该接口
      if (hasMapper(type)) {
        // 如果添加过了，则报错
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }

      // 是否加载完成当前mapper接口的标识
      boolean loadCompleted = false;

      try {
        /*

        2、⚠️构建mapper接口对应的MapperProxyFactory，用于生成mapper接口的代理对象；
        并将mapper接口与MapperProxyFactory的映射关系，添加到knownMappers（已知mapper）集合中

        注意：MapperProxyFactory用于SqlSession.getMapper()的时候，生成mapper接口的代理对象

        */
        // 将Mapper接口对应的Class对象和MapperProxyFactory对象添加到knownMappers集合
        knownMappers.put(type, new MapperProxyFactory<>(type));

        // It's important that the type is added before the parser is run
        // otherwise the binding may automatically be attempted by the
        // mapper parser. If the type is already known, it won't try.
        // 上面的翻译：在运行解析器之前，添加类型很重要，否则映射器解析器可能会自动尝试绑定。如果类型已知，则不会尝试。

        /*

        3、解析mapper
        （1）先解析mapper接口对应的dao.xml文件；—— 配置文件开发
        （2）然后再解析mapper接口（把mapper接口作为映射文件进行解析）—— 注解开发

        */
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        // ⚠️
        parser.parse();

        // 表示当前mapper顺利加载完成
        loadCompleted = true;
      } finally {

        /* 4、如果当前mapper接口，在解析的过程中出错了，则从knownMappers集合中移除当前mapper接口 */

        // 如果loadCompleted=false，表示当前mapper接口在加载过程中出现异常了，需要再将这个mapper接口从knownMappers集合中删除（🤔️这种方式比较丑陋吧，难道是不得已而为之？）
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }

  /**
   * Gets the mappers.
   *
   * @return the mappers
   * @since 3.2.2
   */
  public Collection<Class<?>> getMappers() {
    return Collections.unmodifiableCollection(knownMappers.keySet());
  }


  /**
   * 扫描注册指定包下所有的映射器
   * 1、获取包下所有的Object类型的类，由于接口也是继承Object，所以会被识别到
   * 2、然后会判断，只有是接口，才会进行解析和注册
   * 3、注册mapper（映射器）流程：
   * 3.1、先是构建mapper接口对应的MapperProxyFactory，用于生成mapper接口的代理对象；并将它两的对应关系，存入knownMappers（已知mapper）集合中
   * 3.2、然后去解析mapper，分为2部分：
   * （1）先解析mapper接口对应的dao.xml文件，将对应信息放入Configuration；—— 配置文件开发
   * （2）然后再解析mapper接口（把mapper接口作为映射文件进行解析），将对应信息放入Configuration—— 注解开发
   *
   * Adds the mappers. —— 添加映射器
   * @param packageName 包名称
   * @since 3.2.2
   */
  public void addMappers(String packageName) {
    addMappers(packageName, Object.class/* 查找包下所有类 */);
  }

  /**
   * 注册mapper：
   * 会判断，必须是接口，才会进行加载和解析
   * 1、先是构建mapper接口对应的MapperProxyFactory，用于生成mapper接口的代理对象；并将它两的对应关系，存入knownMappers（已知mapper）集合中
   * 2、然后去解析mapper，分为2部分
   * （1）先解析mapper接口对应的dao.xml文件，将对应信息放入Configuration；—— 配置文件开发
   * （2）然后再解析mapper接口（把mapper接口作为映射文件进行解析），将对应信息放入Configuration—— 注解开发
   *
   * Adds the mappers. —— 添加映射器
   *
   * @param packageName the package name
   * @param superType   the super type
   * @since 3.2.2
   */
  public void addMappers(String packageName, Class<?> superType) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();

    /* 1、查找指定包下的Class（查找指定包下，所有superType类型的类） */
    // 注意：如果superType是Object类型，也就代表获取指定包下所有的类和接口（注意：⚠️接口也继承Object，所以也会被识别到）
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);

    /* 2、获取指定包下，查找到的匹配的Class */
    Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();

    /* 3、遍历Class，注册mapper接口 */
    for (Class<?> mapperClass : mapperSet) {
      /**
       * 注册mapper：
       * 会判断，必须是接口，才会进行加载和解析
       * 1、先是构建mapper接口对应的MapperProxyFactory，用于生成mapper接口的代理对象；并将它两的对应关系，存入knownMappers（已知mapper）集合中
       * 2、然后去解析mapper，分为2部分
       * （1）先解析mapper接口对应的dao.xml文件，将对应信息放入Configuration；—— 配置文件开发
       * （2）然后再解析mapper接口（把mapper接口作为映射文件进行解析），将对应信息放入Configuration—— 注解开发
       */
      // ⚠️注册mapper接口（会判断，只有是接口，才会进行解析和注册）
      addMapper(mapperClass);
    }
  }


}
