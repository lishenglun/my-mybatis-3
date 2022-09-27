package com.msb.other.discriminator.pojo;


import lombok.Data;

@Data
public abstract class HealthReport {

	private int id;

	public abstract String getDescription();

}
