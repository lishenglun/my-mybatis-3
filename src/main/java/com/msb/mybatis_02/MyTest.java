package com.msb.mybatis_02;


import com.msb.mybatis_02.bean.Account;
import com.msb.mybatis_02.bean.User;
import com.msb.mybatis_02.dao.AccountDao;
import com.msb.mybatis_02.dao.UserDao;
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
 * @description 课表地址：mashibing.com/schedule
 * @date 2022/8/8 5:56 下午
 */
public class MyTest {


  /*

  题外：Connection：数据库连接对象
  1. 功能：
	--> 1. 获取执行sql的对象 （通过数据库连接对象获取）
		* Statement createStatement()
		* PreparedStatement prepareStatement(String sql)
	--> 2. 管理事务：
		* 开启事务：void setAutoCommit(boolean autoCommit) ：调用该方法设置参数为false，即开启事务
		* 提交事务：void commit()
		* 回滚事务：rollback()

   */

  /**
   * Mybatis框架因为是对JDBC的封装
   * <p>
   * 一级缓存是Session级别的，当会话关闭，也就是sqlSession关闭，缓存就清空了
   * <p>
   * 二级缓存是SessionFactory级别的，只有sqlSessionFactory，二级缓存才清空。二级缓存里面的数据是可以被多个session所共享的
   *
   * @param args
   */
  public static void main(String[] args) {
    String source = "msb/mybatis_02/mybatis-config.xml";
    InputStream inputStream = null;
    try {
      inputStream = Resources.getResourceAsStream(source);
    } catch (IOException e) {
      e.printStackTrace();
    }

    /*

    1、SqlSessionFactoryBuilder里面，解析mybatis配置文件，得出Configuration对象；
    然后创建SqlSessionFactory；
    将Configuration保存在SqlSessionFactory里面，以方便后续根据配置信息，构建出对应的SqlSession。

     */
    /**
     * SqlSessionFactory：负责创建SqlSession对象的工厂。
     * 在构建SqlSessionFactory对象的时候，就已经把配置文件解析好了，得到一个Configuration，存入在SqlSessionFactory对象里面
     */
    // 解析配置文件得到Configuration，然后通过Configuration创建SqlSessionFactory
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

    /*

    2、通过SqlSessionFactory创建SqlSession对象

    SqlSession（configuration、executor（Configuration、transaction（数据源、隔离级别、是否自动提交）、一级缓存）、autoCommit）

    里面包含了事务对象？事务是与当前的这一次事务挂钩的？

    注意：⚠️里面最重要的是创建了一级缓存！

     */
    /**
     * SqlSession：表示跟数据库建立的一次会话
     */
    // 创建SqlSession
    // 获取数据库的会话，创建出数据库连接的会话对象（事务工厂，事务对象，执行器，如果有插件的话会进行插件的解析）
    // 注意：虽然当前方法的名称叫openSession，但是并未打开会话，也就是并未与数据库建立连接（并未实际获取数据库连接）！只是把会话对象创建出来了，里面包含了一堆属性。
    SqlSession sqlSession = sqlSessionFactory.openSession();


    try {
      /*

       3、获取mapper接口的动态代理对象（每次都是创建一个新的）：
       >>> 1、从configuration.mapperRegistry.knownMappers中，获取mapper接口对应的MapperProxyFactory（不存在就报错）
       >>> 2、根据MapperProxyFactory，创建当前mapper接口对应的动态代理对象（InvocationHandler是MapperProxy，里面包含：SqlSession,mapper接口Class,methodCache）

       题外：MapperProxy里面包了SqlSession，这点很重要，意味着，每个通过相同SqlSession获取的dao接口代理对象中都有着相同的SqlSession，这是它们共有的数据，
       通过SqlSession获取的所有dao接口对象，构成了一个会话！

       注意：⚠️同一个SqlSession可以创建不同接口的不同代理对象，或者创建同一个接口的不同代理对象，无论是哪种，这些代理对象当中的SqlSession都是同一个，
       后续走的都是同一个SqlSession中的同一套逻辑。

       */
      // 从configuration对象中，获取mapper对象（动态代理的方式创建出代理对象）
      // 获取要调用的接口类，创建出对应的mapper的动态代理对象
      UserDao mapper = sqlSession.getMapper(UserDao.class);

      /**
       * 题外：Connection对象的创建是一直延迟到执行SQL语句的时候：
       * >>> 真正连接打开的时间点，只是在我们执行 SQL 语句时，才会进行。其实这样做我们也可以 进一步发现，数据库连接是我们最为宝贵的资源，
       * 只有在要用到的时候，才去获取并打开连接，当我们用完了就再 立即将数据库连接归还到连接池中。
       */
      // 调用方法开始执行

      // MapperProxy#invoke()
      User userByUserResultType = new User();
      userByUserResultType.setId(0);
      userByUserResultType.setUsername("lisi");
      User userByUserResultTypeResult = mapper.getUserByUserResultType(userByUserResultType);
      System.out.println(userByUserResultTypeResult);

      UserDao mapper2 = sqlSession.getMapper(UserDao.class);
      User userByUserResultMap = new User();
      userByUserResultMap.setId(0);
      userByUserResultMap.setUsername("lisi");
      User userByUserResultMapResult = mapper2.getUserByUserResultMap(userByUserResultMap);
      System.out.println(userByUserResultMapResult);

      AccountDao accountDao = sqlSession.getMapper(AccountDao.class);
      List<Account> allAccount = accountDao.getAllAccount();

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


}
