package com.msb.mybatis_02.bean;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/20 11:44 上午
 */
@Data
@ToString
public class Role implements Serializable {

  private Integer id;
  private Integer userId;
  // 角色名称
  private String name;
  // 职位
  private String position;

}
