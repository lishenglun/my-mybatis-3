package com.msb.mybatis_02.bean;

import lombok.Data;
import lombok.ToString;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/21 12:42 上午
 */
@Data
@ToString
public class Account {

  private Integer id;

  private Integer userId;

  // 余额
  private Integer amount;

}
