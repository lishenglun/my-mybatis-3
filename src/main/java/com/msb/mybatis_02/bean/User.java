package com.msb.mybatis_02.bean;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/8 10:06 下午
 */
@Data
@ToString
public class User implements Serializable {

  private Integer id;

  private String name;

  private Integer age;

  private String username;

  private String password;

  private List<Role> roleList;

  private Integer enable;

  private Account account;

}
