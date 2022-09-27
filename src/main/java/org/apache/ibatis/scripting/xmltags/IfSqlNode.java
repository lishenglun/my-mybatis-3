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
package org.apache.ibatis.scripting.xmltags;

/**
 * @author Clinton Begin
 */
public class IfSqlNode implements SqlNode {

  private final ExpressionEvaluator evaluator;
  /**
   *   <select id="getUserByUser" resultType="com.msb.mybatis_02.bean.User" useCache="false">
   *     select *
   *     from user
   *     <where>
   *       <if test="#{id}!=null">
   *         id = #{id}
   *       </if>
   *       <if test="#{username}!=null">
   *         and username = #{username}
   *       </if>
   *     </where>
   *   </select>
   *
   *   test = #{id}!=null
   */
  private final String test;
  private final SqlNode contents;

  public IfSqlNode(SqlNode contents, String test) {
    this.test = test;
    this.contents = contents;
    this.evaluator = new ExpressionEvaluator();
  }

  @Override
  public boolean apply(DynamicContext context) {
    // 用参数对象，判断，表达式是否成立
    // test = 表达式，例如：<if test="#{id}!=null">中的"#{id}!=null"
    // context.getBindings() = 参数对象
    if (evaluator.evaluateBoolean(test, context.getBindings())) {
      contents.apply(context);
      return true;
    }
    return false;
  }

}
