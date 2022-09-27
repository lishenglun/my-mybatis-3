package com.msb.other.resultSets.t_01;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @desc RowMapper 基类处理
 * @author lingdian
 *
 */
@FunctionalInterface
public interface ResultMapper {

	/**
	 * @desc 处理整个ResultSet
	 * @param rs
	 * @return
	 * @throws SQLException
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	default Object handler(ResultSet rs) throws SQLException {
		List result = new ArrayList();
		int row = 1;
		while (rs != null && rs.next()) {
			result.add(handlerRow(rs, row));
			row++;
		}
		return result;
	}

	/**
	 * @desc 处理当条ResultSet
	 * @param rs
	 * @return
	 * @throws SQLException
	 */
	Object handlerRow(ResultSet rs, int rowNum) throws SQLException;

}
