package com.hm.m_01.mybatis.sqlsession.proxy;

import com.hm.m_01.mybatis.cfg.Mapper;
import com.hm.m_01.mybatis.utils.Executor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Map;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2020/5/5 2:43 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public class MapperProxy implements InvocationHandler {

  /**
   * key是由dao的全限定类名和方法名组成的唯一标识
   * value是一个Mapper对象，里面存放的是执行的SQL语句和要封装的实体类全限定类名
   *
   * @param mappers
   */
  private Map<String, Mapper> mappers;
  /**
   * 连接对象
   */
  private Connection con;

  public MapperProxy(Map<String, Mapper> mappers, Connection con) {
    this.mappers = mappers;
    this.con = con;
  }

  /**
   * 对当前正在执行的方法进行增强
   * 取出当前执行的方法名称
   * 取出当前执行的方法所在类
   * 拼接成 key
   * 去 Map 中获取 Value（Mapper)
   * 使用工具类 Executor 的 selectList 方法
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {

    //1.取出方法名
    String name = method.getName();

    //2.取出方法所在类名
    String classname = method.getDeclaringClass().getName();

    //3.拼接成 Key
    String key = classname + "." + name;

    //4.使用 key 取出 mapper
    Mapper mapper = mappers.get(key);
    if (mapper == null) {
      throw new IllegalArgumentException("传入的参数有误，无法获取执行的必要条件");
    }

    //5.创建 Executor 对象
    Executor executor = new Executor();
    return executor.selectList(mapper, con);
  }

}
