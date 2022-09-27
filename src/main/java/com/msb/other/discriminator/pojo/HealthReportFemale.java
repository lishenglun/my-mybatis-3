package com.msb.other.discriminator.pojo;

import lombok.Data;

@Data
public class HealthReportFemale extends HealthReport{

    private String item;

    private Double score;

    private Integer userId;


    @Override
    public String getDescription() {
        String rank = score >= 60 ? "达标" : "不达标";
        return "检查项目："+item+",成绩："+rank;
    }

}
