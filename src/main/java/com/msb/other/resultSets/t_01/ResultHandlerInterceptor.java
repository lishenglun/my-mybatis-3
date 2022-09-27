package com.msb.other.resultSets.t_01;

import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;


/**
 * @desc 对结果集处理
 */
@Intercepts(@Signature(method = "handleResultSets", type = ResultSetHandler.class, args = {java.sql.Statement.class}))
public class ResultHandlerInterceptor implements Interceptor {

  private static Logger logger = LoggerFactory.getLogger(ResultHandlerInterceptor.class);

  @Override
  public Object intercept(Invocation invocation) throws Throwable {  //获取参数值，调用execHandlerResult方法进行处理
    ResultSetHandler resultSetHandler = (ResultSetHandler) invocation.getTarget();

    MappedStatement mappedStatement = (MappedStatement) ReflectionUtils.getFieldValue(resultSetHandler,
      "mappedStatement");
    SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();
    if (sqlCommandType == SqlCommandType.SELECT) {
      String mapper = StringUtil.join(mappedStatement.getResultSets());
      logger.info("ResultHandlerInterceptor.intercept,mapperRow:{}", mapper);
      if (!StringUtil.isEmpty(mapper)) {
        Statement statement = (Statement) invocation.getArgs()[0];
        return execHandlerResult(mapper, statement.getResultSet());
      }
    }
    return invocation.proceed();
  }

  /**
   * 客制化处理resultSet结果集
   *
   * @param rs
   */
  private Object execHandlerResult(String mapper, ResultSet rs) throws SQLException {
    try {
      ResultMapper resultMapper = ApplicationContextUtil.getBeanIgnoreEx(mapper);
      if (resultMapper != null) {
        return resultMapper.handler(rs);
      }
    } finally {
      if (rs != null) {
        rs.close();
      }
    }
    return null;
  }

  @Override
  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  @Override
  public void setProperties(Properties properties) {

  }

}
