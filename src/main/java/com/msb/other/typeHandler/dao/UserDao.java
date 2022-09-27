package com.msb.other.typeHandler.dao;

import com.msb.other.typeHandler.User;
import org.apache.ibatis.session.ResultHandler;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 我们定义的是接口，接口要跟配置文件里面的sql语句映射起来的！
 * @date 2022/8/8 10:05 下午
 */
public interface UserDao {

  void getUserResult(ResultHandler<User> resultHandler);

}
