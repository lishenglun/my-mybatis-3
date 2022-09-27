package com.msb.other.plugins.entity;

import lombok.Data;
import lombok.ToString;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/6 11:10 下午
 */
@Data
@ToString
public class User {

  private Integer id;

  private String username;

  private String password;

  private Integer enable;

}
