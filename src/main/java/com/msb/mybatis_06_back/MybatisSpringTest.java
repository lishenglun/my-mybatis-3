//package com.msb.mybatis_06;
//
//import com.msb.mybatis_06.dao.UserDao;
//import com.msb.mybatis_06.entity.User;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.test.context.ContextConfiguration;
//import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
//
//import java.util.List;
//
///**
// * @author lishenglun
// * @version v1.0.0
// * @description mybatis和spring的整合
// *
// * @date 2022/9/8 6:17 下午
// */
//@ContextConfiguration(locations = {"classpath:msb/mybatis_06/spring.xml"})
//@RunWith(SpringJUnit4ClassRunner.class)
//public class MybatisSpringTest {
//
//  // ⚠️使用spring管理后，mybatis的配置文件可以清空
//
//  // ⚠️编译mybatis-spring源码参考：https://www.cnblogs.com/h--d/p/14742925.html
//
//  @Autowired
//  private UserDao userDao;
//
//  @Test
//  public void test() {
//    List<User> allUser = userDao.getAllUser();
//    allUser.forEach(System.out::println);
//  }
//
//}
