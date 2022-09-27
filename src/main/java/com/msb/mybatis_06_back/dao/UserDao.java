package com.msb.mybatis_06_back.dao;


import com.msb.mybatis_06_back.entity.User;

import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/8 10:52 下午
 */
public interface UserDao {

  List<User> getAllUser();

}
