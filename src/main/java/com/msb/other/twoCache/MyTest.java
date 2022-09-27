package com.msb.other.twoCache;


import com.msb.mybatis_02.bean.User;
import com.msb.mybatis_02.dao.AccountDao;
import com.msb.mybatis_02.dao.UserDao;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 测试二级缓存
 * 课表地址：mashibing.com/schedule
 * @date 2022/8/8 5:56 下午
 */
public class MyTest {

  public static void main(String[] args) {
    String source = "msb/mybatis_02/mybatis-config.xml";
    InputStream inputStream = null;
    try {
      inputStream = Resources.getResourceAsStream(source);
    } catch (IOException e) {
      e.printStackTrace();
    }

    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

    SqlSession sqlSession = sqlSessionFactory.openSession();
    SqlSession sqlSession2 = sqlSessionFactory.openSession();
    try {
      UserDao userDao = sqlSession.getMapper(UserDao.class);
      User user = userDao.getAllUser();

      UserDao accountDao2 = sqlSession.getMapper(UserDao.class);
      User user2 = accountDao2.getAllUser();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      sqlSession.close();
    }
  }


}
