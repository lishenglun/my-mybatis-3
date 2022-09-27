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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 类型处理器（类型转换器的根接口）：用于Java类型与数据库中的数据类型之间的转换
 * (1)将java类型转换为jdbc类型，设置到sql里面；
 * (2)将jdbc类型，转换为java类型，进行返回
 *
 * 题外：java操作数据库，涉及java实体类中的字段和数据库中的字段映射，所以需要进行类型转换，而TypeHandler就是这个作用的。
 * <p>
 * 例如：我要获取Date类型，通过原生的jdbc方式获取到的时间是Timestamp，我要把Timestamp转换为Date，则需要用到：DateTypeHandler。
 * >>> DateTypeHandler里面，先通过原生的jdbc方式获取到时间，是Timestamp类型，然后把Timestamp类型转换为Data类型进行返回！
 * <p>
 * <p>
 * 原生的jdbc，获取对应类型的值，就是ps.getInt()、ps.getString()，
 * 只不过这里抽象为具体的方法了，我在进行调用的时候，直接指定对应的TypeHandler接口进行处理即可，调用的都是公共的，统一的方法，仅此而已
 *
 * @author Clinton Begin
 */
public interface TypeHandler<T> {

  /* 1、设置sql参数 */

  /**
   * 设置sql参数：往？的位置，设置对应类型的参数，比如：往1位置设置Integer类型的参数，往2位置设置String类型的参数
   *
   * @param ps        PreparedStatement
   * @param i         ？的位置
   * @param parameter 参数
   * @param jdbcType  jdbc类型（该参数一般不使用）
   * @throws SQLException
   */
  // 在通过PreparedStatement为SQL语句绑定参数时，会将数据由Java类型转换成JdbcType类型
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  /* 2、获取sql执行的结果 */

  /**
   * 获取结果
   * <p>
   * 从ResultSet中获取数据时会调用此方法，会将数据由JdbcType类型转换成Java类型
   * <p>
   * Gets the result.
   *
   * @param rs         the rs
   * @param columnName Column name, when configuration <code>useColumnLabel</code> is <code>false</code>
   * @return the result
   * @throws SQLException the SQL exception
   */
  T getResult(ResultSet rs, String columnName) throws SQLException;

  T getResult(ResultSet rs, int columnIndex) throws SQLException;

  T getResult(CallableStatement cs, int columnIndex) throws SQLException;


}
