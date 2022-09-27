package com.msb.other.old.dao.impl;

import com.msb.other.old.dao.IUserDao;
import com.msb.other.old.pojo.User;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/22 11:18 上午
 */
public class UserDaoImpl implements IUserDao {

  private SqlSessionFactory factory;

  public UserDaoImpl(SqlSessionFactory factory) {
    this.factory = factory;
  }

  /**
   * 查询所有用户 * @return
   */
  @Override
  public List<User> findAll() {
    try (SqlSession sqlSession = factory.openSession()) {
      return sqlSession.selectList("com.msb.other.old.dao.IUserDao.findAll");
    }
  }

  /**
   * 查询所有用户 * @return
   */
  @Override
  public List<User> getUserList() {
    try (SqlSession sqlSession = factory.openSession()) {
      return sqlSession.selectList("com.msb.other.old.dao.IUserDao.getUserList");
    }
  }

}
