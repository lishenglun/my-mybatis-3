package com.msb.other.old;

import com.msb.other.old.dao.IUserDao;
import com.msb.other.old.dao.impl.UserDaoImpl;
import com.msb.other.old.pojo.User;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;


public class MybatisTest {

  private SqlSessionFactory sqlSessionFactory;

  private IUserDao iUserDao;

  @Before
  public void init() throws IOException {
    // 1.读取配置文件
    InputStream inputStream = Resources.getResourceAsStream("msb/other/old/mybatis-config.xml");
    // 2.创建构建者对象
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

    iUserDao = new UserDaoImpl(sqlSessionFactory);

    inputStream.close();
  }

  @Test
  public void testFindAll() {
    List<User> users = iUserDao.findAll();
    for (User user : users) {
      System.out.println(user);
    }
  }

  @Test
  public void getUserList() {
    List<User> users = iUserDao.getUserList();
    for (User user : users) {
      System.out.println(user);
    }
  }

}
