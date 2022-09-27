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
 * sql语句类型：CRUD
 *
 * @author Clinton Begin
 */
public enum SqlCommandType {

  // 例如：@Options、@SelectKey、<selectKey>定义的sql语句，那么SqlCommandType就是UNKNOWN了
  UNKNOWN,
  INSERT,
  UPDATE,
  DELETE,
  SELECT,
  FLUSH

}
