package com.hm.m_01.mybatis.sqlsession.builder;


import com.hm.m_01.mybatis.sqlsession.SqlSessionFactory;
import com.hm.m_01.mybatis.sqlsession.defaults.DefaultSqlSessionFactory;

import java.io.InputStream;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/5/5 2:15 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public class SqlSessionFactoryBuilder {

    public SqlSessionFactory build(InputStream in) {
      DefaultSqlSessionFactory factory = new DefaultSqlSessionFactory();
      // 给factory中config赋值
      factory.setConfig(in);
      return factory;
    }

}
