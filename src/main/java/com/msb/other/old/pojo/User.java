package com.msb.other.old.pojo;

import lombok.Data;
import lombok.ToString;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/22 11:17 上午
 */
@Data
@ToString
public class User {

  private Integer id;
  private String username;
  private String password;
  private Integer enable;

}
