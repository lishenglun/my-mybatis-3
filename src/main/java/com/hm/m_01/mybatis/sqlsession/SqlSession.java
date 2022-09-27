package com.hm.m_01.mybatis.sqlsession;

import com.hm.m_01.mybatis.cfg.Mapper;
import com.hm.m_01.mybatis.utils.Executor;

import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2020/5/5 2:16 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public interface SqlSession {

  /**
   * 创建 Dao 接口的代理对象
   *
   * @param daoClass
   * @return
   */
  <T> T getMapper(Class<T> daoClass);

  /**
   * 释放资源
   */
  void close();

  /**
   * 查询所有方法
   */
  <E> List<E> selectList(String statement);



}
