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
package org.apache.ibatis.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.util.MapUtil;

/**
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {

  // 目标对象
  private final Object target;
  // 拦截器
  private final Interceptor interceptor;
  // 签名集合（由拦截器上的@Intercepts中的@Signature构成）
  // key：要拦截的类
  // value：要拦截的类中的方法
  private final Map<Class<?>, Set<Method>> signatureMap;

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  /**
   * 如果目标对象实现的接口中，是要被拦截的接口，则对目标对象创建动态代理
   *
   * @param target      目标对象
   * @param interceptor 拦截器
   */
  public static Object wrap(Object target, Interceptor interceptor) {
    /* 1、获取拦截器上面定义的签名集合，里面包含了要拦截的类和要拦截的类中的方法 */
    // 获取签名集合：
    // key：要拦截的类
    // value：要拦截的类的方法（Set<Method>）
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);

    /*

    2、如果当前目标类实现的接口中，有被拦截的接口，则创建动态代理。
    InvocationHandler是Plugin，往Plugin里面设置保存：目标对象、拦截器、签名集合

    */
    // 目标类型
    Class<?> type = target.getClass();
    // 获取当前目标类中实现的要被拦截的接口
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);

    // 如果当前目标类中实现的接口，没有一个是被拦截的，则不会对目标对象创建动态代理；否则创建目标对象的动态代理对象
    if (interfaces.length > 0) {
      // 创建动态代理
      return Proxy.newProxyInstance(
        // 目标类型
        type.getClassLoader(),
        // 目标类型实现的被拦截的接口
        interfaces,
        // ⚠️InvocationHandler
        new Plugin(target, interceptor, signatureMap));
    }

    return target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      /* 1、获取要拦截的类的方法 */
      Set<Method> methods = signatureMap.get(method.getDeclaringClass()/* 方法所在的声明类 */);

      /* 2、如果当前方法属于要拦截的方法，则调用当前拦截器 */
      if (methods != null && methods.contains(method)) {
        return interceptor.intercept(new Invocation(target, method, args));
      }

      /* 3、如果当前方法不属于要拦截的方法，则直接执行当前方法，不经过拦截器 */
      return method.invoke(target, args);
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  /**
   * 获取签名集合：
   * key：java类型
   * value：Set<Method>
   *
   * @param interceptor
   * @return
   */
  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    /* 1、获取拦截器类上的@Intercepts */
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);

    /* 2、拦截器上没有@Intercepts，则报错 */
    // issue #251
    if (interceptsAnnotation == null) {
      // 在拦截器中未找到@Intercepts
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
    }

    /* 3、获取@Intercepts中配置的@Signature数组 */
    Signature[] sigs = interceptsAnnotation.value();

    // 签名集合
    // key：要拦截的类
    // value：要拦截的类的方法（Set<Method>）
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();

    /* 4、遍历@Signature数组，构建签名集合 */
    for (Signature sig : sigs) {
      /* 4.1、获取要拦截的类，作为签名集合key */
      // 不存在key，就设置key，并且创建value；存在的话就获取key对应的value
      // key：要拦截的类
      // value：要拦截的类的方法（Set<Method>）
      Set<Method> methods = MapUtil.computeIfAbsent(signatureMap, sig.type()/* 要拦截的类 */, k -> new HashSet<>());
      /* 4.2、获取要拦截的类中的方法，作为签名集合value */
      try {
        // 通过@Signature中配置的方法名称和参数类型，确定要拦截的类中的具体方法，然后添加到signatureMap的value中
        Method method = sig.type().getMethod(sig.method()/* 方法名称 */, sig.args()/* 方法参数类型 */);
        // 往Set<Method>中设置Method
        methods.add(method);
      } catch (NoSuchMethodException e) {
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
      }
    }

    return signatureMap;
  }

  /**
   * 获取当前目标类中实现的要被拦截的接口
   *
   * @param type
   * @param signatureMap
   * @return
   */
  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    // 存放当前目标类中实现的要被拦截的接口
    Set<Class<?>> interfaces = new HashSet<>();

    while (type != null) {
      // 遍历目标类实现的所有接口
      for (Class<?> c : type.getInterfaces()) {
        // 如果实现的接口是要被拦截的接口，则添加该接口
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      // 获取父类
      type = type.getSuperclass();
    }

    return interfaces.toArray(new Class<?>[0]);
  }

}
