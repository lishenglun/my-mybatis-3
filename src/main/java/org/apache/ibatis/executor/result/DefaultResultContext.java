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
package org.apache.ibatis.executor.result;

import org.apache.ibatis.session.ResultContext;

/**
 * 默认结果上下文
 *
 * @author Clinton Begin
 */
public class DefaultResultContext<T> implements ResultContext<T> {

  // 当前行的结果对象
  // 题外：从下一行的处理逻辑来看，当前resultObject就是上一行的结果对象
  private T resultObject;
  // 累计处理的行数据量
  private int resultCount;
  // 是否停止处理结果映射？
  // true：停止处理
  // false：不停止处理（恒定返回false）
  private boolean stopped;

  public DefaultResultContext() {
    resultObject = null;
    resultCount = 0;
    stopped = false;
  }

  @Override
  public T getResultObject() {
    return resultObject;
  }

  @Override
  public int getResultCount() {
    return resultCount;
  }

  @Override
  public boolean isStopped() {
    return stopped;
  }

  public void nextResultObject(T resultObject) {
    // （1）递增处理的行数据量(DefaultResultContext.resultCount)，该值用于检测处理的记录行数是否己经达到上下（在RowBounds。limit字段中记录了该上限）；
    resultCount++;
    // （2）保存将当前行的结果对象(DefaultResultContext.resultObject)
    this.resultObject = resultObject;
  }

  @Override
  public void stop() {
    this.stopped = true;
  }

}
