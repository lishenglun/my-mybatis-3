package com.msb.mybatis_02.dao;

import com.msb.mybatis_02.bean.Account;

import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/18 1:39 上午
 */
public interface AccountDao {

  List<Account> getAllAccount();

}
