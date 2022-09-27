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
package org.apache.ibatis.session;

/**
 * 结果处理器：对映射好的结果对象，进行后置处理。一般是存储所有的行结果对象。
 *
 * @author Clinton Begin
 */
public interface ResultHandler<T> {

  /**
   * 传入ResultContext，后置处理当前行的结果对象。
   *
   * 一般是存储当前行的结果对象，例如DefaultResultHandler里面就是存储所有行的结果对象！
   *
   * @param resultContext ResultContext：结果上下文，里面存储了当前行的结果对象
   */
  void handleResult(ResultContext<? extends T> resultContext);

}
