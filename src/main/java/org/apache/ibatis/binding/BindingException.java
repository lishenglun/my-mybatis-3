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

import org.apache.ibatis.exceptions.PersistenceException;

/**
 * 绑定异常：（自定义的异常处理器）
 * 当map中查不到对应的key时，抛出此异常，
 * 当重复添加映射时，也抛出此异常
 * 当绑定mapper中某个方法出错时，也抛出此异常
 *
 * @author Clinton Begin
 */
public class BindingException extends PersistenceException {

  private static final long serialVersionUID = 4300802238789381562L;

  public BindingException() {
    super();
  }

  public BindingException(String message) {
    super(message);
  }

  public BindingException(String message, Throwable cause) {
    super(message, cause);
  }

  public BindingException(Throwable cause) {
    super(cause);
  }
}
