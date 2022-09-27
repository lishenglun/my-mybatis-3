package com.msb.other.tx;

import com.msb.other.tx.dao.UserDao;
import com.msb.other.tx.entity.User;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

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
      in = Resources.getResourceAsStream("msb/other/tx/mybatis-config.xml");
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
  public void addUser() throws IOException {

    try {
      User user = new User();
      user.setUsername("lisi");
      user.setPassword("123456");
      user.setEnable(1);
      userDao.addUser(user);

      /**
       * 1、rollback()和close()：对修改过的脏数据进行回滚(true)，变为正常数据；
       * 2、commit()：对修改过的脏数据进行提交，变为正常数据；
       * 3、变为正常数据了，那么rollback()、commit()、close()都无需操作！
       * 总结：所以脏数据：要么回滚，变为正常；要么提交，变为正常
       */

    } catch (Exception e) {
      e.printStackTrace();
      // 回滚
      sqlSession.rollback();
    } finally {
      /**
       * 题外：当然里面也不是直接提交事务，而是根据情况，来决定是否提交，提交的条件是【"关闭自动提交 && 当前操作已经污染数据为脏数据了"】
       * >>> 默认情况下，是关闭自动提交；如果是执行update()——CUD操作，那么数据都是脏数据，所以调用commit()会提交任何CUD操作，然后在提交完毕之后，会把数据标识为正常，这样在后面调用close()/rollback()时，就不会回滚事务了！
       *
       * 注意：出现异常后，会执行rollback()，而后因为commit()是在finally里面，所以一定会被执行，但是没关系，如果前面执行了rollback()，变为正常数据后，后面是不会执行commit()的！
       */
      // 提交事务（CUD操作，必须使用sqlSession.commit()提交务）
      sqlSession.commit();

      /**
       * 注意：⚠️close()里面并不会提交事务，只有可能会回滚事务，所以和sqlSession.commit()搭配，并不冲突！
       * >>> 是否回滚的条件是"关闭自动提交 && 当前操作已经污染数据为脏数据了"。默认情况下，是关闭自动提交；如果是执行update()——CUD操作，那么数据都是脏数据，
       * >>> 所以调用close()，会回滚任何CUD操作，也就是在侧面，要求，想要提交数据，必须执行sqlSession.commit()
       *
       * 注意：close()虽然会回滚事务，但是如果有人回滚了事务，那么就不会再次回滚，所以和sqlSession.rollback()搭配，并不冲突！
       */
      // 当sqlSession关闭掉之后，一级缓存就没有了，缓存对象全部清空掉
      sqlSession.close();
    }
  }

  @After
  public void after() throws Exception {

  }

}
