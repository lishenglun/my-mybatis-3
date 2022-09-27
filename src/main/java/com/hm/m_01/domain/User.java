package com.hm.m_01.domain;

import lombok.Data;
import lombok.ToString;

import java.util.Date;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2020/5/4 5:09 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
@Data
@ToString
public class User {

    private Integer id;
    private String username;
    private Date birthday;
    private String sex;
    private String address;

}
