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

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.util.MapUtil;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -4724728412955527868L;
  private static final int ALLOWED_MODES = MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
    | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC;
  private static final Constructor<Lookup> lookupConstructor;
  private static final Method privateLookupInMethod;
  // 记录了关联的sqLSession对象
  private final SqlSession sqlSession;
  // mapper接口对应的CLass对象
  private final Class<T> mapperInterface;
  // 方法缓存
  // 用于缓存MapperMethod对象，其中key是mapper接口中方法对应的Method对象，value是对应的MapperMethod对象，MapperMethod
  // 以及SQL语句的执行功能，需要注意的是，MapperMethod中并不记录任何状态相关的信息，所以可以在多个代理对象之间共享
  private final Map<Method, MapperMethodInvoker> methodCache;

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethodInvoker> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  static {
    Method privateLookupIn;
    try {
      privateLookupIn = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
    } catch (NoSuchMethodException e) {
      privateLookupIn = null;
    }
    privateLookupInMethod = privateLookupIn;

    Constructor<Lookup> lookup = null;
    if (privateLookupInMethod == null) {
      // JDK 1.8
      try {
        lookup = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
        lookup.setAccessible(true);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(
          "There is neither 'privateLookupIn(Class, Lookup)' nor 'Lookup(Class, int)' method in java.lang.invoke.MethodHandles.",
          e);
      } catch (Exception e) {
        lookup = null;
      }
    }
    lookupConstructor = lookup;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {

      /* 1、判断是不是调用Object的方法，是的话，就直接调用，不走mybatis的处理流程 */

      /**
       * mapper接口动态代理以后，调用所有方法，都是走这个invoke()，但是，并不是所有方法都需要走mybatis的处理流程，
       * 例如：toString()、hashCode()等方法，所以如果是属于Object的方法，则直接调用目标方法即可！
       *
       * 题外：在定义sql语句的id时，可以定义为toString()，但是调用toString()，是不会走mybatis的流程的，而是直接调用目标方法！
       *   <select id="toString" resultType="com.msb.mybatis_02.bean.User">
       *     select *
       *     from user
       *   </select>
       */
      // 如果目标方法的声明类，是Object，则直接调用目标方法，也就是说：如果调用Object的方法，则直接调用，不走mybatis的处理流程！
      // 例如：目标方法是toString()、hashCode()等，则不需要走mybatis的处理流程，直接调用即可！
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      }

      /*

      2、不是调用Object的方法。则从methodCache中获取当前方法对应的MapperMethodInvoker(映射方法调用器)，
      如果没有，则创建一个MapperMethodInvoker对象，并放入methodCache中；然后调用MapperMethodInvoker#invoke()

      */

      // 否则，根据被调用的接口方法method对象，从缓存中获取MapperMethodInvoker对象，如果没有则创建一个并放入缓存，然后调用invoke()方法
      else {
        // cachedInvoker(method)：从缓存中获取"映射方法调用器"(MapperMethodInvoker)，没有就创建
        // PlainMethodInvoker
        return cachedInvoker(method).invoke(proxy, method, args, sqlSession);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  /**
   * 从缓存中获取"映射方法调用器"(MapperMethodInvoker)，没有就创建
   *
   * @param method
   * @return
   * @throws Throwable
   */
  // 获取缓存中MapperMethodInvoker，如果没有则创建一个，而MapperMethodInvoker内部封装这一个MethodHandler
  private MapperMethodInvoker cachedInvoker(Method method) throws Throwable {
    try {
      /* 1、从methodCache中获取当前方法所对应的MapperMethodInvoker */
      // 先从缓存中获取，没有再创建
      return MapUtil.computeIfAbsent(methodCache, method,
        /* 2、如果methodCache中，不存在当前方法所对应的MapperMethodInvoker，则创建一个MapperMethodInvoker返回，并存入methodCache */
        m -> {
          /* 2.1、接口默认方法，则创建"默认方法执行器(DefaultMethodInvoker)"进行返回，分为jdk8版本和jdk9版本 */
          // 如果是调用接口的默认方法，则创建默认方法的执行器进行返回，分为jdk8版本的和jdk9版本的！
          if (m.isDefault()/* jdk8之后，接口可以写默认方法 */) {
            try {
              /* 2.1.1、jdk8版本 */
              if (privateLookupInMethod == null) {
                return new DefaultMethodInvoker/* 默认方法的执行器 */(getMethodHandleJava8(method))/* jdk8版本 */;
              }
              /* 2.1.2、jdk9版本 */
              else {
                return new DefaultMethodInvoker(getMethodHandleJava9(method))/* jdk9版本 */;
              }
            } catch (IllegalAccessException | InstantiationException | InvocationTargetException
              | NoSuchMethodException e) {
              throw new RuntimeException(e);
            }
          }
          /*

          2.2、⚠️不是接口默认方法，则创建"普通方法调用器(PlainMethodInvoker)"，同时会创建一个MapperMethod(映射方法)，存入PlainMethodInvoker中；
          ⚠️创建MapperMethod的时候很关键，在MapperMethod构造方法里面，会创建SqlCommand和MethodSignature(方法签名)
          （1）SqlCommand里面包含了：sql语句的唯一标识(name)和sql语句类型(type)
          （2）MethodSignature里面包含了：一些返回值相关的标识位、关键参数类型的索引位置、创建参数名称解析器(存有"参数索引位置"和"参数名称"之间的对应关系，解析了@Param)

          */
          // 如果当前调用的方法不是接口默认方法，则创建一个PlainMethodInvoker(普通方法调用器)，并创建MapperMethod，放入PlainMethodInvoker里面，
          // 在MapperMethod构造方法里面，创建了SqlCommand和MethodSignature对象，
          // SqlCommand里面包含了：sql语句的唯一标识(name)和sql语句类型(type)
          // MethodSignature里面包含了：一些返回值相关的标识位、关键参数类型的索引位置、创建参数名称解析器(存有"参数索引位置"和"参数名称"之间的对应关系，解析了@Param)
          else {
            /**
             * 1、注意：⚠️new MapperMethod()里面：
             * >>> 1、创建SqlCommand
             * >>> 2、创建MethodSignature
             */
            return new PlainMethodInvoker/* 普通方法调用器 */(new MapperMethod/* 映射方法 */(mapperInterface, method, sqlSession.getConfiguration()));
          }
        });
    } catch (RuntimeException re) {
      Throwable cause = re.getCause();
      throw cause == null ? re : cause;
    }
  }

  private MethodHandle getMethodHandleJava9(Method method)
    throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    final Class<?> declaringClass = method.getDeclaringClass();
    return ((Lookup) privateLookupInMethod.invoke(null, declaringClass, MethodHandles.lookup())).findSpecial(
      declaringClass, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
      declaringClass);
  }

  private MethodHandle getMethodHandleJava8(Method method)
    throws IllegalAccessException, InstantiationException, InvocationTargetException {
    final Class<?> declaringClass = method.getDeclaringClass();
    return lookupConstructor.newInstance(declaringClass, ALLOWED_MODES).unreflectSpecial(method, declaringClass);
  }

  interface MapperMethodInvoker/* 映射器方法调用器 */ {
    Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable;
  }

  private static class PlainMethodInvoker/* 普通方法调用器 */ implements MapperMethodInvoker {

    // 映射方法对象
    private final MapperMethod mapperMethod;

    public PlainMethodInvoker(MapperMethod mapperMethod) {
      super();
      this.mapperMethod = mapperMethod;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
      return mapperMethod.execute(sqlSession, args);
    }

  }

  private static class DefaultMethodInvoker implements MapperMethodInvoker {
    private final MethodHandle methodHandle;

    public DefaultMethodInvoker(MethodHandle methodHandle) {
      super();
      this.methodHandle = methodHandle;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
      return methodHandle.bindTo(proxy).invokeWithArguments(args);
    }
  }
}
