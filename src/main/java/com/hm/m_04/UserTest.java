package com.hm.m_04;

import com.hm.m_04.dao.UserDao;
import com.hm.m_04.entity.User;
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
 * @description TODO
 * @date 2020/5/9 11:16 上午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public class UserTest {

  private InputStream in;
  private SqlSessionFactory sqlSessionFactory = null;
  private SqlSession sqlSession = null;
  private UserDao userDao = null;

  @Before
  public void before() {
    SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
    try {
      in = Resources.getResourceAsStream("hm/m_04/mybatis-configuration.xml");
      sqlSessionFactory = builder.build(in);
      sqlSession = sqlSessionFactory.openSession();
      userDao = sqlSession.getMapper(UserDao.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  @Test
  public void findAll() {
    List<User> all = userDao.findAll();
    for (User user : all) {
      System.out.println(user);
      System.out.println("====================");
    }
  }

  /**
   * 测试一级缓存
   */
  @Test
  public void findById() {
    /**
     * 测试一级缓存的存在
     */
    User user1 = userDao.findById(41);
    System.out.println("第一次查询:" + user1);
    User user2 = userDao.findById(41);
    System.out.println("第e二次查询:" + user2);
    System.out.println(user1 == user2);   // true

    /**
     * 测试 增删改，close，commit导致缓存失效
     */
    sqlSession.commit();
    User user3 = userDao.findById(41);
    System.out.println(user3 == user2); // false

    sqlSession.close();
    sqlSession = sqlSessionFactory.openSession();
    userDao = sqlSession.getMapper(UserDao.class);
    User user4 = userDao.findById(41);
    System.out.println(user4 == user3); // false

    /**
     * clearCache()：这个方法也可以清空一级缓存
     */
    // 清空一级缓存
    sqlSession.clearCache();
    User user5 = userDao.findById(41);
    System.out.println(user4 == user5); // false
  }


  /**
   * 测试缓存的同步：
   * 指在做比如修改操作的时候，会清空了一级缓存，下次获取的时候需要走数据库，并不是在修改的时候去更新了缓存
   */
  @Test
  public void findByIdSynchronized() {
    User user1 = userDao.findById(41);
    System.out.println(user1);

    userDao.updateById(41);

    User user2 = userDao.findById(41);
    System.out.println(user2);
    System.out.println("===========");
    System.out.println(user1 == user2);
  }


  /**
   * 测试二级缓存
   */
  @Test
  public void findByIdTwo() {
    User user1 = userDao.findById(41);
    System.out.println(user1);
//        userDao.updateById(41);
//        sqlSession.commit();
    sqlSession.close();

//        SqlSession sqlSessionUpdate = sqlSessionFactory.openSession();
//        UserDao userDaoUpdate = sqlSessionUpdate.getMapper(UserDao.class);
//        userDaoUpdate.updateById(41);
//        sqlSessionUpdate.commit();

    SqlSession sqlSession = sqlSessionFactory.openSession();
    UserDao userDao2 = sqlSession.getMapper(UserDao.class);
    User user2 = userDao2.findById(41);
    System.out.println(user2);
  }


  @After
  public void after() throws IOException {
    sqlSession.commit();
    sqlSession.close();
    in.close();
  }

}
