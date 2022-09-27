/*
 *    Copyright 2009-2021 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
public abstract class BaseStatementHandler implements StatementHandler {

  protected final Configuration configuration;
  protected final ObjectFactory objectFactory;
  protected final TypeHandlerRegistry typeHandlerRegistry;
  // 记录SQL语句对应的MappedStatement和RowBounds对象
  protected final ResultSetHandler resultSetHandler/* 处理查询到的结果集，也就是处理ResultSet */;
  // RowBounds记录了用户设置的offset和limit,用于在结果集中定位映射的起始位置和结束位置
  protected final ParameterHandler parameterHandler;

  protected final Executor executor;
  protected final MappedStatement mappedStatement;
  protected final RowBounds rowBounds;

  protected BoundSql boundSql;

  /**
   * 最主要干了3件事：
   * （1）获取主键
   * （2）创建ParameterHandler（参数处理器），⚠️并应用插件对其进行扩展
   * （3）创建ResultSetHandler（结果集处理器），⚠️并应用插件对其进行扩展
   *
   * @param executor
   * @param mappedStatement
   * @param parameterObject
   * @param rowBounds
   * @param resultHandler
   * @param boundSql
   */
  protected BaseStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    this.configuration = mappedStatement.getConfiguration();
    this.executor = executor;
    this.mappedStatement = mappedStatement;
    this.rowBounds = rowBounds;

    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();

    /* 1、获取主键 */

    // ⚠️获取主键
    if (boundSql == null) { // issue #435, get the key before calculating the statement —— issue 435，在计算语句之前获取key
      // 调用KeyGenerator.processBefore()获取主键
      generateKeys(parameterObject);
      boundSql = mappedStatement.getBoundSql(parameterObject);
    }

    this.boundSql = boundSql;

    /* 2、创建ParameterHandler（参数处理器），⚠️并应用插件对其进行扩展 */

    // 生成"参数处理器"
    this.parameterHandler = configuration.newParameterHandler(mappedStatement, parameterObject, boundSql);

    /* 3、创建ResultSetHandler（结果集处理器），⚠️并应用插件对其进行扩展 */

    // 生成"结果集处理器"
    this.resultSetHandler = configuration.newResultSetHandler(executor, mappedStatement, rowBounds, parameterHandler, resultHandler, boundSql);
  }

  @Override
  public BoundSql getBoundSql() {
    return boundSql;
  }

  @Override
  public ParameterHandler getParameterHandler() {
    return parameterHandler;
  }

  /**
   * 1、创建执行sql语句的对象：Statement
   * 2、往statement里面，设置超时时间
   * 3、往statement里面，设置读取条数
   *
   * @param connection
   * @param transactionTimeout
   * @return
   * @throws SQLException
   */
  // 准备sql语句
  @Override
  public Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException {
    // 记录一下错误日志
    ErrorContext.instance().sql(boundSql.getSql());
    Statement statement = null;
    try {
      /* 1、创建Statement */
      // PreparedStatementHandler
      statement = instantiateStatement(connection);

      /* 2、往statement里面，设置超时时间 */
      setStatementTimeout(statement, transactionTimeout);

      /* 3、往statement里面，设置读取条数 */
      // 设置读取的数据条数
      setFetchSize(statement);

      return statement;
    } catch (SQLException e) {
      closeStatement(statement);
      throw e;
    } catch (Exception e) {
      closeStatement(statement);
      throw new ExecutorException("Error preparing statement.  Cause: " + e, e);
    }
  }

  protected abstract Statement instantiateStatement(Connection connection) throws SQLException;

  protected void setStatementTimeout(Statement stmt, Integer transactionTimeout) throws SQLException {
    Integer queryTimeout = null;
    if (mappedStatement.getTimeout() != null) {
      queryTimeout = mappedStatement.getTimeout();
    } else if (configuration.getDefaultStatementTimeout() != null) {
      queryTimeout = configuration.getDefaultStatementTimeout();
    }
    if (queryTimeout != null) {
      stmt.setQueryTimeout(queryTimeout);
    }
    StatementUtil.applyTransactionTimeout/* 应用事务超时 */(stmt, queryTimeout, transactionTimeout);
  }

  /**
   * 设置读取条数
   */
  protected void setFetchSize(Statement stmt) throws SQLException {
    /* 1、如果mappedStatement中存在"读取条数"，则优先采用mappedStatement中的"读取条数" */
    Integer fetchSize/* 获取大小 */ = mappedStatement.getFetchSize();
    if (fetchSize != null) {
      // 设置查询条数
      stmt.setFetchSize(fetchSize);
      return;
    }

    /* 2、如果mappedStatement中不存在"读取的数据条数"，则尝试设置configuration中全局的，默认的"读取条数"，没有的话也不设置 */
    Integer defaultFetchSize = configuration.getDefaultFetchSize();
    if (defaultFetchSize != null) {
      // 设置查询条数
      stmt.setFetchSize(defaultFetchSize);
    }
  }

  protected void closeStatement(Statement statement) {
    try {
      if (statement != null) {
        statement.close();
      }
    } catch (SQLException e) {
      //ignore
    }
  }

  protected void generateKeys(Object parameter) {
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();

    ErrorContext.instance().store();

    keyGenerator.processBefore(executor, mappedStatement, null, parameter);

    ErrorContext.instance().recall();
  }

}
