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

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  // MixedSqlNode：混合sql节点
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  /**
   * 1、根据参数对象，判断某些条件是否成立，然后动态组装sql
   * 2、解析动态组装好的sql，变为jdbc可执行的sql
   * 3、同时为每一个sql参数，构建sql参数映射（ParameterMapping，面保存了sql参数名和参数类型）
   * >>> 注意：里面并没有构建sql参数和参数值之前的映射，只是按顺序，相当于保存了一下sql参数名称，以及在参数对象中的属性类型（java类型）
   *
   * @param parameterObject   "参数名"与"实参"之间的对应关系
   */
  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // 构建ContextMap，然后往ContextMap里面设置参数（参数对象、数据库厂商）
    // >>> ContextMap主要包含2个变量：参数对象的MetaObject、是否存在当前参数对象的TypeHandler
    DynamicContext/* 动态上下文 */ context = new DynamicContext(configuration, parameterObject);

    /* 1、根据参数对象，判断某些条件是否成立，然后动态组装sql */

    /**
     * 例如：
     * <select id="getUserByUser" resultType="com.msb.mybatis_02.bean.User" useCache="false">
     *    select *
     *    from user
     *    <where>
     *      <if test="#{id}!=null">
     *        id = #{id}
     *      </if>
     *      <if test="#{username}!=null">
     *        and username = #{username}
     *      </if>
     *    </where>
     *  </select>
     *
     *  如果条件都成立，那么得到的sql语句是：
     *  select * from user WHERE id = #{id} and username = #{username}
     */
    // 根据参数对象，判断某些条件是否成立，然后动态组装sql
    rootSqlNode.apply(context);

    /*

    2、解析sql
    （1）解析动态组装好的sql，变为jdbc可执行的sql
    （2）同时为每一个sql参数，构建sql参数映射（ParameterMapping：面保存了sql参数名和参数类型）

    */
    // 题外：SqlSourceBuilder构造方法里面，就是设置一些变量，没干什么事情
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    // 获取参数对象的类型，如果参数对象为空，则参数类型为Object
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    // ⚠️解析sql
    //（1）解析sql，得到jdbc可执行的sql；
    //（2）同时为每一个sql参数，构建sql参数映射（ParameterMapping，面保存了sql参数名和参数类型）
    // 注意：⚠️这一步执行完毕后，select * from user WHERE id = #{id} and username = #{username}会变成select * from user WHERE id = ? and username = ?，属于jdbc可执行的sql了
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql()/* sql */, parameterType/* 参数对象类型 */, context.getBindings()/* 参数对象 */);

    /* 3、设置附加参数 */
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    context.getBindings().forEach(boundSql::setAdditionalParameter/* 设置附加参数 */);

    return boundSql;
  }

}
