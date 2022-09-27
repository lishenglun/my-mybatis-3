package com.hm.m_01.mybatis.cfg;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2020/5/5 2:13 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
@Data
public class Configuration {

  private String driver;
  private String url;
  private String username;
  private String password;
  /**
   * key是由dao的全限定类名和方法名组成的唯一标识
   * value是一个Mapper对象，里面存放的是执行的SQL语句和要封装的实体类全限定类名
   */
  private Map<String, Mapper> mappers = new HashMap<String, Mapper>();

}
