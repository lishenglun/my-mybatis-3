package com.hm.m_01.test;//package com.itheima.test;

import com.hm.m_01.dao.IUserDao;
import com.hm.m_01.domain.User;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/5/4 5:54 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public class MybatisTest {

  public static void main(String[] args) {

    InputStream in = null;
    SqlSession session = null;
    try {
      //1.读取配置文件
      in = Resources.getResourceAsStream("abc.xml");

      //2.创建 SqlSessionFactory 的构建者对象
      SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();

      //3.通过构建者创建工厂对象
      SqlSessionFactory factory = builder.build(in);

      //4.使用 SqlSessionFactory 生产 SqlSession 对象
      session = factory.openSession();

      //5.使用 SqlSession 创建 dao 接口的代理对象
      IUserDao userDao = session.getMapper(IUserDao.class); //6.使用代理对象执行查询所有方法

      List<User> users = userDao.findAll();
      for (User user : users) {
        System.out.println(user);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      //7.释放资源
      if (session != null) {
        session.close();
      }
      try {
        if (in != null) {
          in.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

  }

}
