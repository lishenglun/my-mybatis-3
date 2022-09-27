package com.hm.m_01.dao;

import com.hm.m_01.domain.User;

import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2020/5/4 5:11 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public interface IUserDao {
  /**
   * 查询所有用户
   *
   * @return
   */
  List<User> findAll();
}
