package com.hm.m_01.mybatis.sqlsession.defaults;

import com.hm.m_01.mybatis.cfg.Configuration;
import com.hm.m_01.mybatis.cfg.Mapper;
import com.hm.m_01.mybatis.sqlsession.SqlSession;
import com.hm.m_01.mybatis.sqlsession.proxy.MapperProxy;
import com.hm.m_01.mybatis.utils.DataSourceUtil;
import com.hm.m_01.mybatis.utils.Executor;
import lombok.Data;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2020/5/5 2:36 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
@Data
public class DefaultSqlSession implements SqlSession {

  // 核心配置对象
  private Configuration cfg;
  // 连接对象
  private Connection conn;

  /**
   * 调用DataSourceUtils工具类获取连接
   */
  public Connection getConn() {
    try {
      //conn = DataSourceUtil.getDataSource(cfg).getConnection();
      conn = DataSourceUtil.getConnection(cfg);
      return conn;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * 获取动态代理对象
   */
  @Override
  public <T> T getMapper(Class<T> daoInterfaceClass) {
    conn = getConn();
    // daoInterfaceClass.getInterfaces() 报错
    // return (T) Proxy.newProxyInstance(daoInterfaceClass.getClassLoader(), daoInterfaceClass.getInterfaces(), new MapperProxy(configuration.getMappers(), connection));
    return (T) Proxy.newProxyInstance(daoInterfaceClass.getClassLoader()
      , new Class[]{daoInterfaceClass}
      /* InvocationHandler */
      , new MapperProxy(cfg.getMappers(), conn));
  }

  // 释放资源
  @Override
  public void close() {
    try {
      conn.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  //查询所有方法
  @Override
  public <E> List<E> selectList(String statement) {
    Map<String, Mapper> mappers = cfg.getMappers();
    Mapper mapper = mappers.get(statement);
    return new Executor().selectList(mapper, conn);
  }

}
