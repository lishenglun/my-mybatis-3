package com.msb.other.discriminator.dao;

import com.msb.other.discriminator.pojo.User;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/20 5:43 下午
 */
public interface UserDao {

  User selectUserAndHealthReportsById(int i);

}
