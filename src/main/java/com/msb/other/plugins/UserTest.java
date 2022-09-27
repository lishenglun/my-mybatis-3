package com.msb.other.plugins;

import com.msb.other.plugins.dao.UserDao;
import com.msb.other.plugins.entity.User;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/6 11:13 下午
 */
public class UserTest {

  private SqlSession sqlSession = null;
  private UserDao userDao;

  @Before
  public void before() {
    SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
    try {
      SqlSessionFactory sqlSessionFactory = builder.build(Resources.getResourceAsStream("msb/other/plugins/mybatis-config.xml"));
      sqlSession = sqlSessionFactory.openSession(true);
      userDao = sqlSession.getMapper(UserDao.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void getAllUser() {
    List<User> allUser = userDao.getAllUser();
    System.out.println(allUser);
  }


  @After
  public void after() {
    if (sqlSession != null) {
      sqlSession.close();
    }
  }


}
