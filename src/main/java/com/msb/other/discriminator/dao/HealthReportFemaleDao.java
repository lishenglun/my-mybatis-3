package com.msb.other.discriminator.dao;

import com.msb.other.discriminator.pojo.HealthReportMale;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/20 5:46 下午
 */
public interface HealthReportFemaleDao {

  HealthReportMale selectByUserId(Integer userId);

}
