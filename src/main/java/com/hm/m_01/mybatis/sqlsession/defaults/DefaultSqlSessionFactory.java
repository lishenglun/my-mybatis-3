package com.hm.m_01.mybatis.sqlsession.defaults;

import com.hm.m_01.mybatis.cfg.Configuration;
import com.hm.m_01.mybatis.sqlsession.SqlSession;
import com.hm.m_01.mybatis.sqlsession.SqlSessionFactory;
import com.hm.m_01.mybatis.utils.XMLConfigBuilder;

import java.io.InputStream;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/5/5 2:34 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public class DefaultSqlSessionFactory implements SqlSessionFactory {

  private InputStream config = null;

  public void setConfig(InputStream config) {
    this.config = config;
  }

  @Override
  public SqlSession openSession() {
    DefaultSqlSession session = new DefaultSqlSession();

    // 调用工具类解析xml文件
    // 1、通过配置文件输入流，读取配置文件
    // 2、读取到配置文件之后，然后解析配置文件，获取数据库的连接信息以及dao.xml文件，并解析mapper文件得到dao接口中的方法签名和sql映射关系，将这些信息都放入一个Configuration对象中！
    Configuration configuration = XMLConfigBuilder.loadConfiguration(config);

    session.setCfg(configuration);

    // 拿着数据库连接信息创建了一个数据库连接
    return session;
  }

}
