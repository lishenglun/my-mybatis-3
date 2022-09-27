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
package org.apache.ibatis.session.defaults;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;

/**
 * @author Clinton Begin
 */
public class DefaultSqlSessionFactory implements SqlSessionFactory {

  private final Configuration configuration;

  public DefaultSqlSessionFactory(Configuration configuration) {
    this.configuration = configuration;
  }

  /**
   * 创建SqlSession对象(DefaultSqlSession)
   *
   * SqlSession（configuration、executor（Configuration、transaction（数据源、隔离级别、是否自动提交））、autoCommit）
   */
  @Override
  public SqlSession openSession() {
    return openSessionFromDataSource(configuration.getDefaultExecutorType()/* 获取默认的执行器 */, null, false);
  }

  @Override
  public SqlSession openSession(boolean autoCommit) {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, autoCommit);
  }

  @Override
  public SqlSession openSession(ExecutorType execType) {
    return openSessionFromDataSource(execType, null, false);
  }

  @Override
  public SqlSession openSession(TransactionIsolationLevel level) {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), level, false);
  }

  @Override
  public SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level) {
    return openSessionFromDataSource(execType, level, false);
  }

  @Override
  public SqlSession openSession(ExecutorType execType, boolean autoCommit) {
    return openSessionFromDataSource(execType, null, autoCommit);
  }

  @Override
  public SqlSession openSession(Connection connection) {
    return openSessionFromConnection(configuration.getDefaultExecutorType(), connection);
  }

  @Override
  public SqlSession openSession(ExecutorType execType, Connection connection) {
    return openSessionFromConnection(execType, connection);
  }

  @Override
  public Configuration getConfiguration() {
    return configuration;
  }

  /**
   * 创建SqlSession对象(DefaultSqlSession)
   *
   * SqlSession（configuration、executor（Configuration、transaction（数据源、隔离级别、是否自动提交））、autoCommit）
   *
   * @param execType
   * @param level
   * @param autoCommit
   * @return
   */
  private SqlSession openSessionFromDataSource/* 从数据源打开会话 */(ExecutorType execType, TransactionIsolationLevel level, boolean autoCommit) {
    Transaction tx = null;
    try {
      /* 1、创建事务对象transaction（里面包含：数据源、隔离级别、是否自动提交） */
      /**
       * 1、要想知道具体的Environment是怎样的，入口是看哪里构建的SqlSessionFactory
       *
       * 因为Environment属于Configuration，而只有在构建SqlSessionFactory的时候，会构建一个Configuration，因为SqlSessionFactory对象里面需要一个Configuration。
       * 所以要想知道具体的Environment是怎样的，入口是看哪里构建的SqlSessionFactory
       *
       *（1）如果是单独的mybatis，我们配置为<transactionManager type="JDBC">，那么Environment中：
       * id = development
       * transactionFactory = JdbcTransactionFactory
       *
       * 参考：{@link org.apache.ibatis.builder.xml.XMLConfigBuilder#environmentsElement(XNode)}
       *
       *（2）如果是整合了Spring，那么Environment中：
       * id = SqlSessionFactoryBean
       * transactionFactory = SpringManagedTransactionFactory（mybatis-spring中的类）
       *
       * 参考：{@link org.mybatis.spring.SqlSessionFactoryBean#afterPropertiesSet()}
       */
      // （1）获取数据库环境对象
      final Environment environment = configuration.getEnvironment();
      // （2）从数据库环境对象里面获取事务工厂对象（也叫，事务管理器）
      final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
      // （3）通过事务工厂创建一个事务对象
      tx = transactionFactory.newTransaction(environment.getDataSource()/* 数据源 */, level/* 隔离级别 */, autoCommit/* 是否自动提交 */);

      /*

      2、根据执行器类型(executorType)，创建当前会话，对应类型的执行器（Executor：里面包含Configuration、transaction、一级缓存）

      题外：里面最重要的是会创建一级缓存！

      */
      // 根据执行器类型，创建对应类型的执行器
      final Executor executor = configuration.newExecutor(tx, execType);

      /* 3、创建SqlSession对象，里面包含configuration、executor、autoCommit */
      // SqlSession（configuration、executor（Configuration、transaction（数据源、隔离级别、是否自动提交））、autoCommit）
      return new DefaultSqlSession(configuration, executor, autoCommit);
    } catch (Exception e) {
      closeTransaction(tx); // may have fetched a connection so lets call close()
      throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  private SqlSession openSessionFromConnection(ExecutorType execType, Connection connection) {
    try {
      boolean autoCommit;
      try {
        autoCommit = connection.getAutoCommit();
      } catch (SQLException e) {
        // Failover to true, as most poor drivers
        // or databases won't support transactions
        autoCommit = true;
      }
      final Environment environment = configuration.getEnvironment();
      final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
      final Transaction tx = transactionFactory.newTransaction(connection);
      final Executor executor = configuration.newExecutor(tx, execType);
      return new DefaultSqlSession(configuration, executor, autoCommit);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  /**
   * 从数据库环境对象里面获取事务工厂对象
   *
   * @param environment 数据库环境对象
   */
  private TransactionFactory getTransactionFactoryFromEnvironment(Environment environment) {
    if (environment == null || environment.getTransactionFactory() == null) {
      return new ManagedTransactionFactory();
    }
    return environment.getTransactionFactory();
  }

  private void closeTransaction(Transaction tx) {
    if (tx != null) {
      try {
        tx.close();
      } catch (SQLException ignore) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

}
