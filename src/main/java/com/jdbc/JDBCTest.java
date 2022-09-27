package com.jdbc;

import java.sql.*;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description jdbc程序的回顾
 * @date 2022/8/11 11:36 上午
 */
public class JDBCTest {

  public static void main(String[] args) throws SQLException {
    Connection connection = null;
    PreparedStatement preparedStatement = null;
    ResultSet resultSet = null;
    try {
      /* 1、加载数据库驱动 */
      Class.forName("com.mysql.jdbc.Driver");

      /* 2、获取连接 */
      // 通过驱动管理类获取数据库链接
      connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/mybatis?characterEncoding=utf-8", "root", "root");
      //connection.setAutoCommit(false);

      /* 3、定义sql */
      // 定义sql语句？表示占位符
      String sql = "select * from user where username = ?";

      /* 4、获取执行sql的对象：Statement */
      //获取预处理statement
      preparedStatement = connection.prepareStatement(sql);

      /* 5、通过Statement，设置参数 */
      // 设置参数，
      // 第一个参数为sql语句中参数的序号(从1开始)
      // 第二个参数为设置的参数值
      preparedStatement.setString(1, "王五");

      /* 6、通过Statement，向数据库发出sql执行查询，得到结果 */
      // 向数据库发出sql执行查询，查询出结果集
      resultSet = preparedStatement.executeQuery();
      // 遍历查询结果集
      while (resultSet.next()) {
        System.out.println(resultSet.getString("id") + " " + resultSet.getString("username"));
      }
    } catch (
      Exception e) {
      e.printStackTrace();
      //connection.rollback();
    } finally { //释放资源
      //connection.commit();
      if (resultSet != null) {
        try {
          resultSet.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
      if (preparedStatement != null) {
        try {
          preparedStatement.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
      if (connection != null) {
        try {
          connection.close();
        } catch (SQLException e) {
          // TODO Auto-generated catch block e.printStackTrace();
        }
      }
    }
  }

}
