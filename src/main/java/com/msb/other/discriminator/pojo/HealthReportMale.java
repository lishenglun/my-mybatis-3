package com.msb.other.discriminator.pojo;

import lombok.Data;

@Data
public class HealthReportMale extends HealthReport{

    private String checkProject;

    private String detail;

    private Integer userId;

    @Override
    public String getDescription() {
        return "检查项目："+checkProject+",成绩："+detail;
    }

}
