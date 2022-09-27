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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;

/**
 * 调用者。
 *
 * 跟method.invoke()表达的意思是一样的，只不过，在调用当前这个Invoker的时候，既可以操作属性，也可以操作方法。
 *
 * 属性操作一共有2个，设置属性和获取属性，所以关于属性的操作一共有2个实现类：GetFieldInvoker、SetFieldInvoker；
 * 方法操作只有1个，所以关于方法操作的实现类只有1个实现类：MethodInvoker
 *
 * @author Clinton Begin
 */
public interface Invoker {

  // 调用获取指定字段的值或执行指定的方法
  Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException;

  // 返回属性相应的类型
  Class<?> getType();

}
