package com.msb.other.typeHandler;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/29 3:28 下午
 */
public class MyTypeHandler implements TypeHandler<User> {

  /**
   * 设置sql参数：往？的位置，设置对应类型的参数，比如：往1位置设置Integer类型的参数，往2位置设置String类型的参数
   *
   * @param ps        PreparedStatement
   * @param i         ？的位置
   * @param parameter 参数
   * @param jdbcType  jdbc类型（该参数一般不使用）
   * @throws SQLException
   */
  @Override
  public void setParameter(PreparedStatement ps, int i, User parameter, JdbcType jdbcType) throws SQLException {

  }

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
  @Override
  public User getResult(ResultSet rs, String columnName) throws SQLException {
    return null;
  }

  @Override
  public User getResult(ResultSet rs, int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public User getResult(CallableStatement cs, int columnIndex) throws SQLException {
    return null;
  }

}
