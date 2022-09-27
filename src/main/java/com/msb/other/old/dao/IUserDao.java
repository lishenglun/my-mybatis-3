package com.msb.other.old.dao;

import com.msb.other.old.pojo.User;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/22 11:17 上午
 */
public interface IUserDao {

  /**
   * 查询所有用户 * @return
   */
  List<User> findAll();

  /**
   * 查询所有用户 * @return
   */
  @Select("select * from user")
  List<User> getUserList();

}
