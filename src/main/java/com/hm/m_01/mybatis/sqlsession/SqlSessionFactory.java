package com.hm.m_01.mybatis.sqlsession;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/5/5 2:16 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public interface SqlSessionFactory {

  /**
   * 创建一个新的 SqlSession 对象
   *
   * @return
   */
  SqlSession openSession();

}
