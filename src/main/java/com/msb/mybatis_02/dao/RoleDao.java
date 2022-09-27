package com.msb.mybatis_02.dao;

import com.msb.mybatis_02.bean.Role;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/20 12:05 下午
 */
public interface RoleDao {

  List<Role> getRoleByUserId(Integer userId);

}
