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

import org.apache.ibatis.reflection.MetaObject;

/**
 * 对象包装器工厂
 *
 * @author Clinton Begin
 */
public interface ObjectWrapperFactory {

  // 有没有包装器
  // 题外：有没有包装器的另一层意思是：对象是否需要加工
  boolean hasWrapperFor(Object object);

  // 获取包装器
  ObjectWrapper getWrapperFor(MetaObject metaObject, Object object);

}
