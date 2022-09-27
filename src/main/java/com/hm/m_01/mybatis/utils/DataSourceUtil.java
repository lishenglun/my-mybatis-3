package com.hm.m_01.mybatis.utils;

import com.hm.m_01.mybatis.cfg.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2020/5/5 3:07 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public class DataSourceUtil {

    /**
     * 创建连接池对象
     */
    public static Connection getConnection(Configuration cfg) {
        try {
            // Class.forName(cfg.getDriver());
            // 直接获取一个连接！
            return DriverManager.getConnection(cfg.getUrl(), cfg.getUsername(), cfg.getPassword());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
