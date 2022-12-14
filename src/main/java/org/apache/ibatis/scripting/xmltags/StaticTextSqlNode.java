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
 * 静态文本Sql节点
 *
 * @author Clinton Begin
 */
public class StaticTextSqlNode implements SqlNode {

  /**
   * 标签内的sql语句，例如：
   * <selectKey keyProperty="id" resultType="int" order="BEFORE">
   *     select CAST(RANDOM()*1000000 as INTEGER) a from SYSIBM.SYSDUMMY1
   * </selectKey>
   *
   * 得到的就是：select CAST(RANDOM()*1000000 as INTEGER) a from SYSIBM.SYSDUMMY1
   */
  private final String text;

  public StaticTextSqlNode(String text) {
    this.text = text;
  }

  @Override
  public boolean apply(DynamicContext context) {
    // 将文本加入context
    context.appendSql(text);
    return true;
  }

}
