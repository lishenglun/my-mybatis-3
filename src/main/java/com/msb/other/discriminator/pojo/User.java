package com.msb.other.discriminator.pojo;

import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/20 5:35 下午
 */
@Data
@ToString
public class User {

  private Integer id;

  private String userName;

  private String realName;

  private String sex;

  private String mobile;

  private String email;

  private String note;

  private Integer positionId;

  private List<HealthReport> healthReports;

  public User() {

  }

  public User(Integer id, String userName) {
    this.id = id;
    this.userName = userName;
  }

}
