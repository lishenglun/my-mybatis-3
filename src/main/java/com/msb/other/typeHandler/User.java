package com.msb.other.typeHandler;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/8 10:06 下午
 */
@Data
@ToString
public class User implements Serializable {

  private Long id;

  private String username;

  private String password;

  private Integer enable;

}
