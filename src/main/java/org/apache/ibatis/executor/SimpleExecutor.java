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
package org.apache.ibatis.executor;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * 简单执行器
 *
 * @author Clinton Begin
 */
public class SimpleExecutor extends BaseExecutor {

  public SimpleExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      stmt = prepareStatement(handler, ms.getStatementLog());
      return handler.update(stmt);
    } finally {
      closeStatement(stmt);
    }
  }

  /**
   * @param ms
   * @param parameter     实参数对象
   * @param rowBounds     分页
   * @param resultHandler
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    // JDBC中的Statement对象
    Statement stmt = null;
    try {
      /* 1、获取Configuration */
      Configuration configuration = ms.getConfiguration();

      /*

      2、创建StatementHandler（语句处理器），并应用插件对RoutingStatementHandler进行扩展
      （1）在StatementHandler构造器里面只做了一件事：根据MappedStatement中配置的StatementType（默认是PreparedStatementHandler），创建对应的StatementHandler。
      （2）然后在StatementHandler的构造器里面，最主要干了3件事：
      >>>（1）获取主键
      >>>（2）创建ParameterHandler（参数处理器），⚠️并应用插件对其进行扩展
      >>>（3）创建ResultSetHandler（结果集处理器），⚠️并应用插件对其进行扩展

      */
      // 创建StatementHandler对象，实际返回的是RoutingStatementHandler对象
      // StatementHandler：sql语句处理器
      StatementHandler handler = configuration.newStatementHandler(wrapper/* 包装的Executor */, ms, parameter, rowBounds, resultHandler, boundSql);

      /*

       3、
       （1）获取数据库连接
        题外：是通过事务对象获取连接，然后在事务对象内部，又是通过数据源获取连接

       （2）创建Statement和往Statement里面设置超时时间和读取条数

       （3）设置sql参数
        根据，之前BoundSql中构建的参数映射（ParameterMapping）中获取参数名，和实参数对象，获取参数值；
        然后通过TypeHandler，向指定索引位置，设置对应类型的参数值。⚠️TypeHandler最终也是调用Statement，通过Statement，设置sql参数值。

       */
      // 创建Statement和初始化Statement
      // 里面获取了连接，以及替换了sql语句中的占位符，得到了一条可以执行的完整的sql语句
      stmt = prepareStatement(handler, ms.getStatementLog());

      /*

      4、调用StatementHandler，向数据库执行sql语句，进行查询，并进行结果映射，返回结果对象

       */
      // 调用query方法执行sql语句，并通过ResultSetHandler完成结果集的映射
      // PreparedStatementHandler
      return handler.query(stmt, resultHandler);
    } finally {
      // 关闭Statement对象
      closeStatement(stmt);
    }
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    Cursor<E> cursor = handler.queryCursor(stmt);
    stmt.closeOnCompletion();
    return cursor;
  }

  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) {
    return Collections.emptyList();
  }

  /**
   * 做了3件事：
   * （1）获取数据库连接
   * 题外：是通过事务对象获取连接，然后在事务对象内部，又是通过数据源获取连接
   * <p>
   * （2）创建Statement，和往Statement里面设置超时时间和读取条数
   * <p>
   * （3）设置sql参数
   * 根据，之前BoundSql中构建的参数映射（ParameterMapping）中获取参数名，和实参数对象，获取参数值；
   * 然后通过TypeHandler，向指定索引位置，设置对应类型的参数值。⚠️TypeHandler最终也是调用Statement，通过Statement，设置sql参数值。
   *
   * @param handler
   * @param statementLog
   * @return
   * @throws SQLException
   */
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    /*

    1、获取数据库连接

    题外：是通过事务对象获取连接，然后在事务对象内部，又是通过数据源获取连接

    */
    // 里面通过数据源获取数据库连接
    // 题外：只有在执行sql语句之前，才会获取连接，如果不是马上要执行sql语句了，前面做的一堆预处理工作的时候，都不会去获取数据库连接
    Connection connection = getConnection(statementLog);

    /*

    2、创建Statement和设置Statement
    （1）创建Statement对象（创建执行sql语句的对象：Statement）
    （2）往statement里面，设置超时时间
    （3）往statement里面，设置读取条数

    注意：⚠️在创建Statement的时候，已经把sql语句交给Statement了，Statement中已经存在sql语句了，只是还没有设置参数值

     */
    // BaseStatementHandler
    stmt = handler.prepare(connection, transaction.getTimeout()/* 获取超时时间 */);

    /*

    3、设置sql参数：
   （1）根据，之前getBoundSql()中构建的参数映射（ParameterMapping）中获取参数名、实参数对象；然后根据参数名去实参对象中，获取参数值；
   （2）然后通过TypeHandler，向指定索引位置，设置对应类型的参数值。
   >>> 注意：⚠️TypeHandler最终也是调用Statement，通过Statement，设置sql参数值。
   >>> 而之所以要️TypeHandler，是通过不同类型的️TypeHandler，调用不同的Statement方法，设置不同类型的参数值到sql语句中
   >>> 例如：IntegerTypeHandler，调用的是ps.setInt(i, parameter);
   >>> 例如：StringTypeHandler，调用的是ps.setString(i, parameter);

     */
    // 处理sql语句中的占位符
    // PreparedStatementHandler
    handler.parameterize(stmt);
    return stmt;
  }

}
