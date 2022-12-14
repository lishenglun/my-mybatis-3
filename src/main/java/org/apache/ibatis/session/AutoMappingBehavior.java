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
 * 自动映射行为的枚举类
 *
 * Specifies if and how MyBatis should automatically map columns to fields/properties. —— 指定MyBatis是否以及如何自动将列映射到字段属性。
 *
 * @author Eduardo Macarron
 */
public enum AutoMappingBehavior/* 自动映射行为 */ {

  /**
   * 禁用自动映射
   *
   * Disables auto-mapping. —— 禁用自动映射。
   */
  NONE/* 没有 */,

  /**
   * 只自动映射结果，不会映射嵌套的结果
   *
   * Will only auto-map results with no nested result mappings defined inside.
   * 只会自动映射结果，其中没有定义嵌套的结果映射。
   */
  PARTIAL/* 部分的 */,

  /**
   * 自动映射所有复杂的结果，包含映射嵌套的结果
   *
   * Will auto-map result mappings of any complexity (containing nested or otherwise).
   * 将自动映射任何复杂性的结果映射（包含嵌套或其他）。
   */
  FULL/* 全部 */

}
