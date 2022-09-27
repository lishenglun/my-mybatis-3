package com.hm.m_01.mybatis;

import com.hm.m_01.dao.IUserDao;
import com.hm.m_01.domain.User;
import com.hm.m_01.mybatis.io.Resources;
import com.hm.m_01.mybatis.sqlsession.SqlSession;
import com.hm.m_01.mybatis.sqlsession.SqlSessionFactory;
import com.hm.m_01.mybatis.sqlsession.builder.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2020/5/5 2:13 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public class MybatisTest {

    /**
     * 1、获取mybatis配置文件的输入流
     * 2、通过配置文件输入流读取配置文件
     * 3、读取到配置文件之后，然后解析配置文件，获取数据库的连接信息以及dao.xml文件所在的位置，
     * >>> 读取并解析所有dao.xml文件得到dao接口中的方法签名和sql语句以及返回类型，封装为一个Mapper对象，
     * >>> 并把方法签名作为key，value是sql和返回类型，放入一个map中，
     * >>> 然后将数据库的连接信息、map放入Configuration对象当中
     * 4、拿着数据库连接信息创建了一个数据库连接
     * 5、创建dao接口对应的动态代理！动态代理的InvocationHandler包含一个数据库连接对象和Configuration对象
     * 6、执行接口方法，就是调用动态代理对象进行执行该方法，具体是调用InvocationHandler#invo()执行
     * >>> 在InvocationHandler#invo()内部，会通过方法签名拿到对应的sql和返回类型，然后通过连接对象执行sql，获取结果
     * >>> 最后把结果封装为对应的返回类型，进行返回
     * >>> (入参是直接传入方法即可，动态代理可以获取得到方法的参数！)
     */
    public static void main(String[] args) {
        InputStream in = null;
        SqlSession session = null;
        try {
            //1.读取配置文件
            in = Resources.getResourceAsStream("abc.xml");
            //2.创建 SqlSessionFactory 的构建者对象
            SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
            //3.通过构建者创建工厂对象  - 具体是 DefaultSqlSessionFactory 的向上转型
            SqlSessionFactory factory = builder.build(in);

            //4、创建SqlSession，里面解析了配置文件，得到了configuration
            session = factory.openSession();

            //5.使用 SqlSession 创建 dao 接口的代理对象 -> 用的是 DefaultSqlSession 执行的
            IUserDao userDao = session.getMapper(IUserDao.class);

            //6.使用代理对象执行查询所有方法
            List<User> users = userDao.findAll();
            for (User user : users) {
                System.out.println(user);
            }
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
