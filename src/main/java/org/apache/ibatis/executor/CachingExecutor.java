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

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * 二级缓存执行器（操作二级缓存的）
 *
 * 在创建sqlSession的时候，里面会创建普通的Executor，创建完毕普通的Executor之后，会根据configuration.cacheEnabled属性来决定是否采用CachingExecutor来增强普通的Executor
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class CachingExecutor implements Executor/*,HaHa*/ {

  // 实际的执行器
  private final Executor delegate;

  private final TransactionalCacheManager/* 事务缓存管理器 */ tcm = new TransactionalCacheManager();

  public CachingExecutor(Executor delegate) {
    this.delegate = delegate;
    delegate.setExecutorWrapper(this);
  }

  @Override
  public Transaction getTransaction() {
    return delegate.getTransaction();
  }

  /**
   * 关闭当前CachingExecutor
   *
   * @param forceRollback   是否需要提交或回滚。true：回滚，false：提交
   */
  @Override
  public void close(boolean forceRollback/* 强制回滚 */) {
    try {
      /*

      1、如果是回滚，则调用"事务缓存管理器"，进行回滚：
      遍历当前SqlSession中操作过的的二级缓存，对每个二级缓存进行：
      (1)移除二级缓存中"缺失的条目"，也就是：二级缓存中没有哪个CacheKey对应的数据，就移除这个CacheKey
      (2)重置当前二级缓存的TransactionalCache为刚创建时的样子

      */
      // issues #499, #524 and #573
      if (forceRollback) {
        tcm.rollback();
      }
      /*

      2、不是回滚，则调用"事务缓存管理器"，进行提交：
      遍历当前SqlSession中操作过的的二级缓存，对每个二级缓存进行：
      (1)如果当前二级缓存被标识为"commit时需要清空"，则清空当前二级缓存中的数据
      (2)将"commit时需要往二级缓存中添加的数据"，缓存到二级缓存
      (3)重置当前二级缓存对应的TransactionalCache为刚创建时的样子

      */
      else {
        tcm.commit();
      }
    }
    /* 3、⚠️调用装饰的Executor.close()，里面会清空一级缓存、根据需要回滚事务 */
    finally {
      // BaseExecutor
      // 题外：只有当forceRollback=true，才会进行回滚，否则不会回滚，也不会提交。这里面只决定了是否回滚。所以和sqlSession.commit()搭配，并不冲突！
      delegate.close(forceRollback);
    }
  }

  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override
  public int update(MappedStatement ms, Object parameterObject) throws SQLException {
    // 刷新缓存完再update
    flushCacheIfRequired(ms);
    return delegate.update(ms, parameterObject);
  }

  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    flushCacheIfRequired(ms);
    return delegate.queryCursor(ms, parameter, rowBounds);
  }

  /**
   * 查询
   *
   * @param ms                    MappedStatement
   * @param parameterObject       "参数名"与"实参(入参对象)"之间的对应关系，方便后面填入sql语句中
   * @param rowBounds             分页相关
   * @param resultHandler         resultHandler
   */
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    /*

     1、BoundSql —— 构建jdbc可执行sql，和构建sql参数映射（注意：里面并没有构建sql参数和参数值之前的映射，只是按顺序，相当于保存了一下sql参数名称，以及在参数对象中的属性类型(java类型)）：
    （1）根据参数对象，判断某些条件是否成立，然后动态组装sql
    （2）解析动态组装好的sql，变为jdbc可执行的sql
    （3）同时为每一个sql参数，构建sql参数映射（ParameterMapping，面保存了sql参数名和参数类型）
     >>> 注意：里面并没有构建sql参数和参数值之前的映射，只是按顺序，相当于保存了一下sql参数名称，以及在参数对象中的属性类型(java类型)

     */
    BoundSql boundSql = ms.getBoundSql(parameterObject);

    /* 2、创建CacheKey(一级/二级用的都是同一个CacheKey) */
    // 创建CacheKey对象
    // 题外：这个CacheKey是作为一级缓存的名称
    CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);

    /* 3、查询 */
    return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  /**
   * 查询
   *
   * @param ms                    MappedStatement
   * @param parameterObject       "参数名"与"实参(入参对象)"之间的对应关系，方便后面填入sql语句中
   * @param rowBounds             分页相关
   * @param resultHandler         resultHandler
   * @param key                   CacheKey
   * @param boundSql              里面具备jdbc可执行sql，和sql参数映射
   */
  // 题外：被ResultLoader.selectList调用
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
    throws SQLException {
    /* 1、获取二级缓存（mapper级别的，或者说是命名空间级别的） */

    // 获取二级缓存（这个二级缓存的id是当前sql语句所在的命名空间，也就是说，这个二级缓存是当前sql语句所在命名空间级别对应的缓存，也就是mapper级别的缓存）
    Cache cache = ms.getCache();

    /* 2、当前mapper开启了二级缓存 */
    // 是否开启了二级缓存
    if (cache != null) {
      /*

      2.1、根据flushCache属性判断一下，是否要清除当前二级缓存(注意：⚠️这里只是标识一下在commit时需要清除当前二级缓存，并未真正清除！另外，如果当前二级缓存被标识为在commit时需要被清除，则也代表当前二级缓存中的数据是无效的、"当前二级缓存的数据"是作为已清除的数据、当前二级缓存是没有数据的，即使有数据，也代表没有数据，是空的)

      题外：flushCache属性：决定是否要刷新一/二级缓存（默认值：当前是select操作的话，默认值为false，也就是不刷新缓存；当前操作不是select操作的话，则默认为true，代表要刷新缓存）

      */
      flushCacheIfRequired/* 如果需要，刷新缓存 */(ms);      // 🚩判断是否要清除当前二级缓存

      /* 2.2、当前查询语句需要使用二级缓存，则从二级缓存中查询数据 */
      /**
       * 题外：只有select操作才有userCache配置！
       */
      // 当前查询语句需要使用二级缓存 && resultHandler为null（默认，就是null）
      if (ms.isUseCache() && resultHandler == null) {
        // 二级缓存不能保存输出类型的参数，如果查询操作调用了包含输出参数的存储过程，则报错
        ensureNoOutParams/* 确保没有输出参数 */(ms, boundSql);
        @SuppressWarnings("unchecked")
        // 从二级缓存中查询数据
        List<E> list = (List<E>) tcm/* 事务缓存管理器 */.getObject(cache, key);    // 🚩如果上述二级缓存是被标识为已清除，那么获取的就直接是null

        /* 2.3、二级缓存中没有数据，则调用封装的执行器(Executor)去数据库查询 */
        if (list == null) {
          // BaseExecutor
          list = delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
          /*

           2.4、将查询到的数据暂存到TransactionalCache.entriesToAddOnCommit(commit时往二级缓存中添加的数据集合)，
           后续在sqlSession.close()的时候，会调用TransactionalCache#flushPendingEntries()，将entriesToAddOnCommit中的数据添加到二级缓存中，
           所以可以直接理解为就是往二级缓存中添加查询到的数据！

           */
          tcm.putObject(cache, key, list); // issue #578 and #116     // 🚩放置查询到的暂存到entriesToAddOnCommit —— "sqlSession.commit时需要往二级缓存中添加的数据"集合
        }

        // 返回查询到的结果
        return list;
      }
    }

    /* 3、没有开启二级缓存，直接调用封装的执行器(Executor)去数据库查询 */
    // 没有启动二级缓存，直接调用底层Executor执行数据库查询操作
    // BaseExecutor
    return delegate.query(ms, parameterObject, rowBounds, resultHandler, key/* 一级缓存的CacheKey */, boundSql);
  }

  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return delegate.flushStatements();
  }

  /**
   * （1）清空一级缓存
   * （2）遍历当前SqlSession中操作过的的二级缓存，对每个二级缓存进行操作：
   *  >>> (1)如果当前二级缓存被标识为commit时需要清空，则清空当前二级缓存中的数据
   *  >>> (2)接着，将"commit时需要往二级缓存中添加的数据"，缓存到二级缓存
   *  >>> 题外：也就是说，上面清空完毕二级缓存，下面又接着添加数据，如果有的话
   *  >>> (3)重置当前二级缓存对应的TransactionalCache为刚创建时的样子
   * @param required
   * @throws SQLException
   */
  @Override
  public void commit(boolean required) throws SQLException {
    /* 1、调用装饰的Executor.commit()，里面会清空一级缓存、提交事务 */
    delegate.commit(required);

    /*

    2、遍历当前SqlSession中操作过的的二级缓存，对每个二级缓存进行：
    (1)如果当前二级缓存被标识为commit时需要清空，则清空当前二级缓存中的数据
    (2)接着，将"commit时需要往二级缓存中添加的数据"，缓存到二级缓存
    题外：也就是说，上面清空完毕二级缓存，下面又接着添加数据，如果有的话
    (3)重置当前二级缓存对应的TransactionalCache为刚创建时的样子

     */
    tcm.commit();
  }

  @Override
  public void rollback(boolean required) throws SQLException {
    try {
      delegate.rollback(required);
    } finally {
      if (required) {
        tcm.rollback();
      }
    }
  }

  // 二级缓存不能保存输出参数，如果查询操作，调用了包含输出参数的存储过程，则报错
  private void ensureNoOutParams/* 确保没有输出参数 */(MappedStatement ms, BoundSql boundSql) {
    if (ms.getStatementType() == StatementType.CALLABLE/* 存储过程 */) {
      // 遍历sql参数映射
      for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
        if (parameterMapping.getMode() != ParameterMode.IN) {
          // 不支持使用OUT参数缓存存储过程。请在“+ms.getId()+”语句中配置useCache=false。
          throw new ExecutorException("Caching stored procedures with OUT params is not supported.  Please configure useCache=false in " + ms.getId() + " statement.");
        }
      }
    }
  }

  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    // BaseExecutor
    return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
  }

  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return delegate.isCached(ms, key);
  }

  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    // SimpleExecutor，走的是BaseExecutor
    delegate.deferLoad(ms, resultObject, property, key, targetType);
  }

  @Override
  public void clearLocalCache() {
    delegate.clearLocalCache();
  }

  /**
   * 根据flushCache属性判断一下，是否要清除二级缓存（注意：⚠️这里只是标识一下在commit时需要清除当前二级缓存，并未真正清除！另外，如果当前二级缓存被标识为在commit时需要被清除，则也代表当前二级缓存中的数据是无效的、"当前二级缓存的数据"是作为已清除的数据、当前二级缓存是没有数据的，即使有数据，也代表没有数据，是空的）
   *
   * flushCache属性：决定是否要刷新一/二级缓存（默认值：当前是select操作的话，默认值为false，也就是不刷新缓存；当前操作不是select操作的话，则默认为true，代表要刷新缓存）
   *
   * @param ms
   */
  private void flushCacheIfRequired/* 如果需要，刷新缓存 */(MappedStatement ms) {
    /* 1、获取二级缓存 */
    Cache cache = ms.getCache();

    /* 2、存在二级缓存，并且需要刷新二级缓存，那就清除二级缓存 */
    // 注意：⚠️这里只是标识一下在commit时需要清除当前二级缓存，并未真正清除！另外，如果当前二级缓存被标识为在commit时需要被清除，则也代表当前二级缓存中的数据是无效的、"当前二级缓存的数据"是作为已清除的数据、当前二级缓存是没有数据的，即使有数据，也代表没有数据，是空的
    // flushCache属性：决定是否要刷新一/二级缓存（默认值：当前是select操作的话，默认值为false，也就是不刷新缓存；当前操作不是select操作的话，则默认为true，代表要刷新缓存）
    if (cache != null && ms.isFlushCacheRequired()) {
      // 清除二级缓存
      tcm.clear(cache);
    }
  }

  @Override
  public void setExecutorWrapper(Executor executor) {
    throw new UnsupportedOperationException("This method should not be called");
  }

}
