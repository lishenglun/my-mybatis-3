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
package org.apache.ibatis.mapping;

/**
 * 执行sql语句的对象类型
 *
 * @author Clinton Begin
 */
public enum StatementType {

  /**
   * 1、PreparedStatement和Statement的区别：
   * PreparedStatement是预处理，用来避免sql注入问题
   *
   * 2、默认情况下，为了避免sql注入，选择的是PREPARED
   */

  // 原生的sql语句/普通的sql语句
  // 代表：执行sql语句的时候，选择Statement对象执行sql语句
  STATEMENT,/* 声明 */

  // 预处理sql语句
  // 代表：执行sql语句的时候，选择PreparedStatement对象
  PREPARED/* 准备好的 */,

  // 存储过程（生产环境几乎不用）
  CALLABLE/* 可调用 */

}
