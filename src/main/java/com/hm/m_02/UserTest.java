package com.hm.m_02;

import com.hm.m_02.dao.UserDao;
import com.hm.m_02.entity.User;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

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
      in = Resources.getResourceAsStream("hm/m_02/mybatis-configuration.xml");
      sqlSessionFactory = builder.build(in);
      sqlSession = sqlSessionFactory.openSession();
      userDao = sqlSession.getMapper(UserDao.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * 测试事务
   */
  @Test
  public void addUser() {
    User user = new User();
    user.setUsername("lisi");
    user.setBirthday(new Date());
    user.setAddress("china");
    user.setSex("男");
    userDao.addUser(user);
  }

  @After
  public void after() throws Exception {
    /**
     * 打开Mysql数据库发现并没有添加任何记录，原因是什么？
     * 这一点和jdbc是一样的，我们在实现增删改时一定要去控制事务的提交，那么在 mybatis 中如何控制事务提交呢？
     * 可以使用：session.commit();来实现事务提交。加入事务提交后的代码如下：
     */
    // 提交事务
    //sqlSession.commit();
    sqlSession.close();
    in.close();
  }

}
