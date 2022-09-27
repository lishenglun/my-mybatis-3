package com.msb.other.tx.entity;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/5/9 10:28 上午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
@Data
@ToString
public class User implements Serializable {

  private Integer id;
  private String username;
  private String password;
  private Integer enable;


}
