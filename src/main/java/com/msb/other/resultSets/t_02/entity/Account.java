package com.msb.other.resultSets.t_02.entity;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/5/9 10:28 上午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public class Account {

  private Integer id;
  private Integer uid;
  private Integer money;

  private User user;

  public void find() {
    System.out.println(uid);
  }

  @Override
  public String toString() {
    return "Account{" +
      "id=" + id +
      ", uid=" + uid +
      ", money=" + money +
      ", user=" + user +
      '}';
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Integer getUid() {
    return uid;
  }

  public void setUid(Integer uid) {
    this.uid = uid;
  }

  public Integer getMoney() {
    return money;
  }

  public void setMoney(Integer money) {
    this.money = money;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }


}
