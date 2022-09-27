package com.msb.other.resultSets.t_01;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component("financeInvoiceItemResultMapper")
public class FinanceInvoiceItemResultMapper implements ResultMapper {

  @Override
  public Object handler(ResultSet rs) throws SQLException {

    BaseExportList<BdInvoiceExport> list = new BaseExportList<>();

    int row = 1;
    while (rs != null && rs.next()) {
      list.add(rs.getLong(1) + "", (BdInvoiceExport) handlerRow(rs, row));
      row++;
    }

    return list;
  }

  @Override
  public Object handlerRow(ResultSet resultSet, int i) throws SQLException {
    BdInvoiceExport bdInvoiceExport = new BdInvoiceExport();
    bdInvoiceExport.setId(resultSet.getLong(1));
    bdInvoiceExport.setName(resultSet.getString(2));
    // 这里省去了其他的子段，实际上有很多
    // ...
    return bdInvoiceExport;
  }

}
