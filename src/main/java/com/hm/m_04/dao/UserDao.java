package com.hm.m_04.dao;

import com.hm.m_04.entity.User;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/5/9 10:29 上午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public interface UserDao {

    List<User> findAll();

    User findById(@Param("id") Integer id);

    Integer updateById(@Param("id") Integer id);



}
