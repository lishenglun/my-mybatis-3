package com.msb.mybatis_01;


import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/8 5:56 下午
 */
public class MyTest {

    public static void main(String[] args) {
        String source = "mybatis-config.xml";
        InputStream inputStream = null;
        try {
            inputStream = Resources.getResourceAsStream(source);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 根据全局配置文件创建出SqLSessionFactory
        // SqlSessionFactory：负责创建SqlSession对象的工厂
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        // SqlSession：表示跟数据库建立的一次会话
        // 获取数据库的会话
        // 注意：这一步，并未实际获取数据库连接！
        SqlSession sqlSession = sqlSessionFactory.openSession();

        User user = null;
        try {
            UserDao mapper = sqlSession.getMapper(UserDao.class);
            user = mapper.getUser(1);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sqlSession.close();
        }

        System.out.println(user);

    }


}