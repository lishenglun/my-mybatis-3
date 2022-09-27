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
package org.apache.ibatis.executor.parameter;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 参数处理器
 *
 * A parameter handler sets the parameters of the {@code PreparedStatement}.
 *
 * @author Clinton Begin
 */
public interface ParameterHandler {

  // 当我们获取到用户传递进来的参数的时候，会涉及到类型的转换，sql参数的复制，等待一系列操作，由ParameterHandler定义规范

  // 获取参数
  // 获取参数对象，参数对象里面存储了sql参数
  Object getParameterObject();

  // 设置参数
  // 往sql语句中，设置sql参数，消除类似?占位符，形成一条可执行的sql
  void setParameters(PreparedStatement ps) throws SQLException;

}
