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

import java.util.Properties;

/**
 * 拦截器
 *
 * 1、这玩意就代表了插件
 *
 * 2、插件的原理和是如何生效的
 *
 * "应用插件的对象"实现了什么接口，然后我定义对应的拦截器，拦截了对应的接口，就会对"应用插件的对象"生成动态代理对象。
 * 后续调用代理对象方法，如果当前方法是要被拦截的方法，就会调用拦截器进行处理；否则直接调用原生对象方法，不经过拦截器。
 *
 * @author Clinton Begin
 */
public interface Interceptor {

  /**
   * 执行拦截器逻辑
   *
   * @param invocation 里面包含：目标对象、目标方法、方法参数
   */
  Object intercept(Invocation invocation) throws Throwable;

  /**
   * 对目标对象应用插件，说白了就是：如果目标对象实现的接口中，是要被拦截的接口，则对目标对象创建动态代理
   *
   * @param target 目标对象
   * @return 动态代理后的目标对象
   */
  default Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  /**
   * 设置拦截器需要的属性，因为可以这样往拦截器中配置属性 —— 根据配置初始化Interceptor对象
   *
   * <plugins>
   * <plugin interceptor="org.mybatis.example.ExamplePlugin">
   * <!-- 往拦截器中设置属性 -->
   * <property name="someProperty" value="100"/>
   * </plugin>
   * </plugins>
   *
   * @param properties
   */
  default void setProperties(Properties properties) {
    // NOP
  }

}
