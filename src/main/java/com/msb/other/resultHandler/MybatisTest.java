package com.msb.other.resultHandler;

import com.msb.other.resultHandler.bean.User;
import com.msb.other.resultHandler.dao.UserDao;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.*;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;


public class MybatisTest {

  private SqlSessionFactory sqlSessionFactory;

  private UserDao userDao;

  @Before
  public void init() throws IOException {
    // 1.读取配置文件
    InputStream inputStream = Resources.getResourceAsStream("msb/other/resultHandler/mybatis-config.xml");
    // 2.创建构建者对象
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

    SqlSession sqlSession = sqlSessionFactory.openSession();
    userDao = sqlSession.getMapper(UserDao.class);

    inputStream.close();
  }

  /**
   * 写法1
   */
  @Test
  public void testFindAll() {
    userDao.getUserResult(new MyResultHandler());
  }

  /**
   * 写法1
   */
  @Test
  public void testFindAll2() {
    userDao.getUserResult(resultContext -> {
      User resultObject = resultContext.getResultObject();
      Long id = resultObject.getId();
      //写数据处理业务
      System.out.println(id);
    });
  }


}
