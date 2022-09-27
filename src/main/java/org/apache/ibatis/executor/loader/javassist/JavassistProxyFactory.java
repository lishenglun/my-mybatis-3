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
package org.apache.ibatis.executor.loader.javassist;

import java.lang.reflect.Method;
import java.util.*;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.Proxy;
import javassist.util.proxy.ProxyFactory;

import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.loader.AbstractEnhancedDeserializationProxy;
import org.apache.ibatis.executor.loader.AbstractSerialStateHolder;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.loader.WriteReplaceInterface;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyCopier;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.session.Configuration;

/**
 * @author Eduardo Macarron
 */
public class JavassistProxyFactory implements org.apache.ibatis.executor.loader.ProxyFactory {

  private static final String FINALIZE_METHOD = "finalize";
  private static final String WRITE_REPLACE_METHOD = "writeReplace"/* 写替换 */;

  public JavassistProxyFactory() {
    try {
      Resources.classForName("javassist.util.proxy.ProxyFactory");
    } catch (Throwable e) {
      throw new IllegalStateException("Cannot enable lazy loading because Javassist is not available. Add Javassist to your classpath.", e);
    }
  }

  /**
   * 创建代理对象
   *
   * @param target              结果对象
   * @param lazyLoader          ResultLoaderMap
   * @param configuration       configuration
   * @param objectFactory       ObjectFactory
   * @param constructorArgTypes 构造器参数类型
   * @param constructorArgs     构造器参数
   * @return
   */
  @Override
  public Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration,
                            ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    return EnhancedResultObjectProxyImpl.createProxy(target, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
  }

  public Object createDeserializationProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    return EnhancedDeserializationProxyImpl.createProxy(target, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
  }

  static Object crateProxy(Class<?> type, MethodHandler callback, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {

    ProxyFactory enhancer = new ProxyFactory();
    // 设置需要代理的类
    enhancer.setSuperclass(type);

    try {
      type.getDeclaredMethod/* 获取声明的方法 */(WRITE_REPLACE_METHOD/* writeReplace *//* 写替换 */);
      // ObjectOutputStream will call writeReplace of objects returned by writeReplace —— ObjectOutputStream将调用writeReplace返回的对象的writeReplace
      if (LogHolder.log.isDebugEnabled()) {
        LogHolder.log.debug(WRITE_REPLACE_METHOD + " method was found on bean " + type + ", make sure it returns this");
      }
    } catch (NoSuchMethodException e) {
      // ⚠️一般是走这里
      enhancer.setInterfaces(new Class[]{WriteReplaceInterface.class});
    } catch (SecurityException e) {
      // nothing to do here
    }

    // 动态代理对象
    Object enhanced;

    // 构造器参数类型
    Class<?>[] typesArray = constructorArgTypes.toArray(new Class[constructorArgTypes.size()]);
    // 构造器参数值
    Object[] valuesArray = constructorArgs.toArray(new Object[constructorArgs.size()]);

    try {
      // ⚠️创建动态代理
      enhanced = enhancer.create(typesArray/* 构造器参数类型 */, valuesArray/* 构造器参数值 */);
    } catch (Exception e) {
      throw new ExecutorException("Error creating lazy proxy.  Cause: " + e, e);
    }

    // ⚠️设置拦截器
    ((Proxy) enhanced).setHandler(callback);

    return enhanced;
  }

  private static class EnhancedResultObjectProxyImpl implements MethodHandler {

    // 结果对象的类型
    private final Class<?> type;
    //
    private final ResultLoaderMap lazyLoader;
    // true：调用任意方法都会立即加载对象的所有"延迟加载属性"；
    // false：每个"延迟加载属性"按需加载
    private final boolean aggressive;
    // 延迟加载的触发方法
    // 对象的哪些方法会触发一次延迟加载 ("equals", "clone", "hashCode", "toString")
    private final Set<String> lazyLoadTriggerMethods;
    private final ObjectFactory objectFactory;
    // 构造器参数类型
    private final List<Class<?>> constructorArgTypes;
    // 构造器参数
    private final List<Object> constructorArgs;

    private EnhancedResultObjectProxyImpl(Class<?> type, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory,
                                          List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      this.type = type;
      this.lazyLoader = lazyLoader;
      this.aggressive = configuration.isAggressiveLazyLoading();
      //this.lazyLoadTriggerMethods = configuration.getLazyLoadTriggerMethods();
      // 自己写的用于测试的代码：
      this.lazyLoadTriggerMethods = new HashSet<>(Arrays.asList("equals", "clone", "hashCode","find"));
      this.objectFactory = objectFactory;
      this.constructorArgTypes = constructorArgTypes;
      this.constructorArgs = constructorArgs;
    }

    public static Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory,
                                     List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      // 目标类型
      final Class<?> type = target.getClass();

      // 拦截器（将来走EnhancedResultObjectProxyImpl#invoke()）
      EnhancedResultObjectProxyImpl callback = new EnhancedResultObjectProxyImpl(type, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);

      // 创建动态代理对象
      Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);

      // 把原生对象中的属性值复制到动态代理对象中
      PropertyCopier.copyBeanProperties(type, target, enhanced);

      return enhanced;
    }

    // EnhancedResultObjectProxyImpl#invoke()
    @Override
    public Object invoke(Object enhanced/* 代理对象 */, Method method/* 原生代理对象中的方法对象 */, Method methodProxy/* 代理对象中的方法对象 */, Object[] args/* 参数值 */) throws Throwable {
      final String methodName = method.getName();
      try {
        synchronized (lazyLoader) {

          if (WRITE_REPLACE_METHOD/* writeReplace */.equals(methodName)) {
            Object original;
            if (constructorArgTypes.isEmpty()) {
              original = objectFactory.create(type);
            } else {
              original = objectFactory.create(type, constructorArgTypes, constructorArgs);
            }
            // 复制属性
            PropertyCopier.copyBeanProperties(type, enhanced, original);
            if (lazyLoader.size() > 0) {
              return new JavassistSerialStateHolder(original, lazyLoader.getProperties(), objectFactory, constructorArgTypes, constructorArgs);
            } else {
              return original;
            }
          } else {
            // lazyLoader中有ResultLoader(结果加载器) && 当前方法不是finalize()
            if (lazyLoader.size() > 0 && !FINALIZE_METHOD/* finalize */.equals(methodName)) {
              /* 1、当前方法是触发延迟加载的方法，则️触发所有的延迟加载：通过ResultLoader加载数据，和设置属性值 */
              // aggressive = true || 当前方法是触发延迟加载的方法
              if (aggressive || lazyLoadTriggerMethods.contains(methodName)) {
                // ⚠️触发所有的延迟加载：通过ResultLoader加载数据，和设置属性值
                lazyLoader.loadAll();
              } else if (PropertyNamer.isSetter(methodName)) {
                final String property = PropertyNamer.methodToProperty(methodName);
                lazyLoader.remove(property);
              } else if (PropertyNamer.isGetter(methodName)) {
                final String property = PropertyNamer.methodToProperty(methodName);
                if (lazyLoader.hasLoader(property)) {
                  lazyLoader.load(property);
                }
              }
            }
          }
        }
        /**
         * 题外：如果是调用method.invoke(enhanced, args);则又会回到当前方法，
         * >>> 而调用methodProxy.invoke(enhanced, args);，则不会
         */
        // 之所调用的是methodProxy，是因为要往代理对象中设置属性值，所以是调用代理对象对应的方法，才能往代理对象中设置属性值
        return methodProxy.invoke(enhanced, args);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    }
  }

  private static class EnhancedDeserializationProxyImpl extends AbstractEnhancedDeserializationProxy implements MethodHandler {

    private EnhancedDeserializationProxyImpl(Class<?> type, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                                             List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      super(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
    }

    public static Object createProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                                     List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      final Class<?> type = target.getClass();
      EnhancedDeserializationProxyImpl callback = new EnhancedDeserializationProxyImpl(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
      Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
      PropertyCopier.copyBeanProperties(type, target, enhanced);
      return enhanced;
    }

    @Override
    public Object invoke(Object enhanced, Method method, Method methodProxy, Object[] args) throws Throwable {
      final Object o = super.invoke(enhanced, method, args);
      return o instanceof AbstractSerialStateHolder ? o : methodProxy.invoke(o, args);
    }

    @Override
    protected AbstractSerialStateHolder newSerialStateHolder(Object userBean, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
                                                             List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      return new JavassistSerialStateHolder(userBean, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
    }
  }

  private static class LogHolder {
    private static final Log log = LogFactory.getLog(JavassistProxyFactory.class);
  }

}
