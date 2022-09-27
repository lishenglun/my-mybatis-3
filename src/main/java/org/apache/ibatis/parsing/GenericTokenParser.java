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
package org.apache.ibatis.parsing;

/**
 * 普通记号解析器，处理#{}和${}参数
 *
 * @author Clinton Begin
 */
public class GenericTokenParser {

  //有一个开始和结束记号
  // 开始记号
  // 例如：${
  private final String openToken;
  // 结束记号
  // 例如：}
  private final String closeToken;
  // 记号处理器？？？
  // 例如：DynamicCheckerTokenParser
  // 题外：⚠️sql参数映射保存在里面
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  /**
   * 1、解析sql，得到jdbc可执行的sql；
   *
   * 例如：text = select * from user WHERE id = #{id} and username = #{username}
   * 最终，builder = select * from user WHERE id = ? and username = ?
   *
   * 2、同时为每一个sql参数，构建sql参数映射（ParameterMapping）
   *
   * 例如：#{id}中的id，会构建id对应的ParameterMapping对象，并且获取id这个属性名称在参数对象中的属性类型，比如，User中id属性类型为Integer，
   * 那么就会获取到id在参数对象User中的属性类型为Integer，然后创建ParameterMapping对象，ParameterMapping对象里面保存了id和id属性类型
   *
   * @param text
   * @return  jdbc可执行的sql
   */
  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }

    // search open token —— 搜索开放令牌
    // 查找第一个"#{"出现的位置
    int start = text.indexOf(openToken);
    if (start == -1) {
      return text;
    }

    char[] src = text.toCharArray();

    // 偏移量
    int offset = 0;

    /**
     * 例如：
     * text = select * from user WHERE id = #{id} and username = #{username}
     * 最终，builder = select * from user WHERE id = ? and username = ?
     */
    // sql语句
    final StringBuilder builder = new StringBuilder();

    /**
     * 例如：
     * text = select * from user WHERE id = #{id} and username = #{username}
     * 那么 expression等于#{id}中的id，然后在下一个while循环，expression会被清空，等于#{username}中的username
     */
    // 找到的表达式
    StringBuilder expression = null;

    // #{favouriteSection,jdbcType=VARCHAR}
    // 这里是循环解析参数，参考GenericTokenParserTest,比如可以解析${first_name} ${initial} ${last_name} reporting.这样的字符串,里面有3个 ${}
    do {
      // 判断一下 ${ 前面是否是反斜杠，这个逻辑在老版的mybatis中（如3.1.0）是没有的
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        // 新版已经没有调用substring了，改为调用如下的offset方式，提高了效率
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
        // found open token. let's search close token. —— 找到打开的令牌。让我们搜索关闭令牌。
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);  // 清空
        }

        /**
         * 例如：
         * text = select * from user WHERE id = #{id} and username = #{username}
         * 1、第一个da逻辑：builder = select * from user WHERE id =
         * 2、第二个da逻辑：builder = select * from user WHERE id = ? and username =
         */
        // 拼接"#{"之前的内容
        builder.append(src, offset, start - offset);

        // 获取#{结束的位置，作为新的偏移量
        offset = start + openToken.length();

        // 从#{结束的位置，开始搜索"}"出现的位置
        int end = text.indexOf(closeToken, offset/* 开始搜索的索引 */);

        // 如果【end > -1】成立，代表找到了"#{"之后，"}"出现的位置
        while (end > -1) {
          // 忽略，之前版本的
          if (end > offset && src[end - 1] == '\\') {
            // this close token is escaped. remove the backslash and continue.
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          }
          // ⚠️
          else {
            /**
             * 例如：text = select * from user WHERE id = #{id}
             * expression = #{id}中的id
             */
            // 截取"#{结束的位置"到""}"出现的位置"之前的字符串，作为表达式，
            expression.append(src, offset /* #{结束的位置 */, end - offset/* "}"出现的位置 - #{结束的位置 */);   // 例如：id
            break;
          }
        }

        // 没有找到"#{"之后，"}"出现的位置
        if (end == -1) {
          // close token was not found. —— 未找到关闭令牌。
          builder.append(src, start, src.length - start);
          offset = src.length;
        }
        // 找到了"#{"之后，"}"出现的位置
        else {
          /* 构建sql参数映射 */
          /**
           * 1、⚠️handler.handleToken(expression.toString())：构建sql参数映射（属性，属性类型）
           * （1）——> ParameterMappingTokenHandler#handleToken()：返回"?"号，同时构建参数映射
           * 例如：expression = id，那么就会判断参数对象中是否存在id这个属性，存在的话就获取id属性的类型，并保存"id"属性名和对应的"属性类型"
           */
          // ⚠️构建sql参数映射，返回"?"号
          String s = handler.handleToken(expression.toString());
          // 拼接上"?"号
          builder.append(s);
          offset = end + closeToken.length();   // 第一个"}"的位置
        }
      }

      // ⚠️再次寻找下一个"#{"出现的位置，找到了就继续循环处理
      start = text.indexOf(openToken, offset);

    } while (start > -1);


    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
