package com.msb.other.discriminator;

import com.msb.other.discriminator.dao.UserDao;
import com.msb.other.discriminator.pojo.User;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;


public class MybatisTest {

  private SqlSessionFactory sqlSessionFactory;

  @Before
  public void init() throws IOException {
    String resource = "msb/other/discriminator/mybatis-config.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    inputStream.close();
  }

  /**
   * 测试鉴别器
   */
  @Test
  public void testDiscriminator() {
    SqlSession sqlSession = sqlSessionFactory.openSession();

    UserDao userMapper = sqlSession.getMapper(UserDao.class);

    User user = userMapper.selectUserAndHealthReportsById(1);
    System.out.println(user);

    User user1 = userMapper.selectUserAndHealthReportsById(3);
    System.out.println(user1);
  }

}
