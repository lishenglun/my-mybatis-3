package com.hm.m_04;

import com.hm.m_04.dao.AccountDao;
import com.hm.m_04.entity.Account;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 测试只查账户信息不查用户信息
 * @date 2020/5/9 11:00 上午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public class AccountTest {

  private InputStream in;
  private SqlSession sqlSession = null;
  private AccountDao accountDao = null;

  @Before
  public void before() {
    SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
    try {
      in = Resources.getResourceAsStream("hm/m_04/mybatis-configuration.xml");
      SqlSessionFactory sqlSessionFactory = builder.build(in);
      sqlSession = sqlSessionFactory.openSession(true);
      accountDao = sqlSession.getMapper(AccountDao.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * 测试延迟加载
   */
  @Test
  public void findAll() {
    List<Account> all = accountDao.findAll();
    for (Account account : all) {
      System.out.println(account);
    }
  }

  /**
   * 测试自定义方法的延迟加载 —— find()
   */
  @Test
  public void findAllAndById() {
    Account account = accountDao.findAllAndById(1);
    account.find();
    System.out.println("=============");
  }

  @After
  public void after() throws IOException {
    sqlSession.commit();
    sqlSession.close();
    in.close();
  }

}
