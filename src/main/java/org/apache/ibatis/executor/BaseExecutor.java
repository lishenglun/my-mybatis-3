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

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * 执行器基类
 *
 * @author Clinton Begin
 */
public abstract class BaseExecutor implements Executor {

  private static final Log log = LogFactory.getLog(BaseExecutor.class);

  // Transaction对象，实现事务的提交、回滚和关闭操作
  protected Transaction transaction;
  // 包装自己
  protected Executor wrapper;

  /**
   * 1、添加DeferredLoad的地方：
   * 属性映射，通过嵌套查询获取属性值的时候，如果一级缓存中存在CacheKey对应的数据，但是是执行占位符，则会将当前属性名对应的DeferredLoad，添加到deferredLoads中
   * <p>
   * 参考：BaseExecutor#deferLoad()
   */
  // 延迟加载队列（线程安全）
  protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;

  // 一级缓存，用于缓存该Executor对象查询结果集映射得到的结果对象（缓存查询到的结果，所对应的映射对象）
  // 简单概括：缓存查询到的结果
  protected PerpetualCache localCache;

  // 一级缓存，用于缓存输出类型的参数（缓存输出参数）
  // 简单概括：缓存输出参数
  protected PerpetualCache localOutputParameterCache/* 本地输出参数缓存 */;

  // 用来记录嵌套查询的层数
  protected Configuration configuration;

  // 用来记录嵌套查询的层数
  protected int queryStack;
  // 当前Executor是否关闭了的标识（true：关闭；false：未关闭）
  // 很多地方要用到这个标识来判断Executor是否关闭，只有当Executor没有关闭，才能操作Executor的相关方法
  private boolean closed;

  protected BaseExecutor(Configuration configuration, Transaction transaction) {
    // ⚠️transaction
    this.transaction = transaction;
    // 延迟队列
    this.deferredLoads = new ConcurrentLinkedQueue<>();
    // ⚠️一级缓存，用于缓存当前Executor(执行器)查询结果集映射得到的结果对象
    this.localCache = new PerpetualCache("LocalCache");
    // ⚠️一级缓存，用于缓存输出类型的参数
    this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
    // 当前Executor是否关闭了的标识（true：关闭；false：未关闭）
    // 很多地方要用到这个标识来判断Executor是否关闭，只有当Executor没有关闭，才能操作Executor的相关方法
    this.closed = false;
    // ⚠️configuration
    this.configuration = configuration;
    // 包装自己
    this.wrapper = this;
  }

  @Override
  public Transaction getTransaction() {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return transaction;
  }

  /**
   * 关闭当前Executor
   *
   * @param forceRollback 是否需要提交或回滚。true：回滚，false：提交
   */
  @Override
  public void close(boolean forceRollback) {
    try {
      try {
        /*

         1、
         (1)清空一级缓存
         (2)刷新Statements（一般什么都没做）
         (3)根据需要回滚事务

         */
        rollback(forceRollback);
      } finally {
        if (transaction != null) {
          // SpringManagedTransaction.close()：释放连接
          transaction.close();
        }
      }
    } catch (SQLException e) {
      // Ignore. There's nothing that can be done at this point.
      log.warn("Unexpected exception on closing transaction.  Cause: " + e);
    } finally {
      // 清空事务对象
      transaction = null;

      // 清空延迟队列
      deferredLoads = null;

      // 置空一级缓存
      // 题外：上面在rollback()当中，是调用
      localCache = null;
      // 置空一级缓存
      localOutputParameterCache = null;

      // ⚠️标识当前Executor为已关闭
      closed = true;
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  // SqLSession.update/insert/delete会调用此方法
  @Override
  public int update(MappedStatement ms, Object parameter) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
    // 判断当前Executor是否己经关闭
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // 先清局部缓存，再更新。如何更新交由子类，模板方法模式
    clearLocalCache();
    // 执行sql语句
    return doUpdate(ms, parameter);
  }

  // 刷新语句，Batch用
  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return flushStatements(false);
  }

  public List<BatchResult> flushStatements/* 刷新Statements */(boolean isRollBack) throws SQLException {
    // 判断当前Executor是否己经关闭
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    /**
     * SimpleExecutor：返回一个空的BatchResult
     */
    // isRollBack：表示执行Executor中缓存的SQL语句。false表示执行，true表示不执行。
    return doFlushStatements(isRollBack);
  }

  // SqlSession.selectList会调用此方法
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    // 获取BoundSql对象
    BoundSql boundSql = ms.getBoundSql(parameter);
    // 创建CacheKey对象
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
    // 查询
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
  }

  /**
   * 查询
   *
   * @param ms            MappedStatement
   * @param parameter     "参数名"与"实参(入参对象)"之间的对应关系，方便后面填入sql语句中
   * @param rowBounds     分页相关
   * @param resultHandler resultHandler
   * @param key           CacheKey
   * @param boundSql      里面具备jdbc可执行sql，和sql参数映射
   */
  @SuppressWarnings("unchecked")
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter/* 参数对象 */, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
    // 检测当前Executor执行器是否己经关闭
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }

    /* 1、【非嵌套查询 && flushCache属性为true】时，清空一级缓存 */
    if (queryStack == 0 && ms.isFlushCacheRequired()/* 是否需要刷新缓存 */) {
      clearLocalCache();
    }

    List<E> list;
    try {
      // 增加查询层数
      queryStack++;
      /* 2、从一级缓存中查询数据，有的话就用一级缓存中的参数 */
      /**
       * 题外：从这里可以看出，一级缓存和二级缓存用的是同一个CacheKey
       */
      // 如果resultHandler为null，则从一级缓存中查询数据
      list = resultHandler == null ? (List<E>) localCache.getObject(key)/* 查询一级缓存 */ : null;
      if (list != null) {
        // 针对存储过程调用的处理：在一级缓存命中时，获取缓存中保存的输出类型参数，并设置到用户传入的实参对象中
        handleLocallyCachedOutputParameters/* 处理本地缓存的输出参数 */(ms, key, parameter, boundSql);
      }
      /* 3、一级缓存中没有，则去查询数据库 */
      else {
        // ⚠️从数据库中查询，并得到映射后的结果对象
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }
    } finally {
      // 当前查询完成，查询层数减少
      queryStack--;
    }

    if (queryStack == 0) {

      /* 4、所有的查询都完成了，相关缓存项，该加载的也全部加载完成了，所以接着触发DeferredLoad(延迟加载)，加载一级缓存中的数据，作为属性值，设置到对应的单行结果对象中 */
      // 验证当前队列里面有没有数据
      // 在最外层的查询结束时，所有嵌套查询也己经完成，相关缓存项也己经完全加载，所以在此处触发DeferredLoad，加载一级缓存中的数据，作为属性值，设置到对应的单行结果对象中
      for (DeferredLoad deferredLoad : deferredLoads) {
        // 从一级缓存中获取数据，作为属性值，设置到结果对象中
        deferredLoad.load();
      }

      // issue #601
      // 加载完成后，清空deferredLoads集合
      deferredLoads.clear();

      // 如果本地缓存范围是STATEMENT级别，则清空一级缓存（默认是LocalCacheScope.SESSION，不是LocalCacheScope.STATEMENT）
      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
        // issue #482
        // 根据LocalCacheScope配置决定是否清空一级缓存
        clearLocalCache();
      }
    }

    /* 5、返回查询到的结果 */
    return list;
  }

  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    return doQueryCursor(ms, parameter, rowBounds, boundSql);
  }

  /**
   * 一级缓存中有就立即加载；一级缓存中没有就放入延迟加载队列，延迟加载：
   * (1)如果一级缓存中存在要查询语句的CacheKey的数据，且不是"执行占位符"，则直接从一级缓存中获取数据，作为属性值，设置到结果对象中；
   * (2)如果一级缓存中不存在，就将创建的DeferredLoad对象，放入deferredLoads(延迟加载队列)中，用于后续延迟加载
   * <p>
   * 调用者：⚠️属性映射，通过嵌套查询获取属性值的时候，如果一级缓存中存在CacheKey对应的数据，则会走这里的逻辑！
   * 题外：DefaultResultSet+andler.getNestedQueryMappingValue()，也就是嵌套查询调用
   *
   * @param ms
   * @param resultObject 结果对象对应的MetaObject，用于设置属性值
   * @param property     属性名
   * @param key          嵌套查询对应的CacheKey
   * @param targetType
   */
  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }

    /* 1、创建当前属性名对应的DeferredLoad(延迟加载) */
    DeferredLoad deferredLoad = new DeferredLoad/* 延迟加载 */(resultObject/* 结果对象对应的MetaObject，可用于设置属性 */, property, key, localCache, configuration, targetType);

    /* 2、判断一级缓存中是否存在要查询语句的CacheKey的数据。如果一级缓存中存在要查询语句的CacheKey的数据，且不是"执行占位符"，则直接从一级缓存中获取数据，作为属性值，设置到结果对象中 */
    if (deferredLoad.canLoad()) {
      // 从一级缓存中获取数据，作为属性值，设置到结果对象中
      deferredLoad.load();
    }

    /* 3、一级缓存中不存在，就将DeferredLoad放入deferredLoads(延迟加载队列)中 */
    else {
      // 将DeferredLoad对象添加到DeferredLoads队列中，待整个外层查询结束后，再加载该结果对象
      deferredLoads/* 延迟加载队列 */.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
    }
  }

  /**
   * 创建缓存key：当前sql语句查询的，所有需要的关联属性和所有相关的参数
   * <p>
   * <p>
   * cacheKey：接口的包名+类名+方法名+参数+sql语句
   *
   * @param ms
   * @param parameterObject
   * @param rowBounds
   * @param boundSql
   * @return
   */
  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    // 检测当前executor执行器是否己经关闭
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // 创建CacheKey对象
    CacheKey cacheKey = new CacheKey();
    // 将MappedStatement的id添加到CacheKey对象中
    cacheKey.update(ms.getId());
    // 将offset添加到CacheKey对象中
    cacheKey.update(rowBounds.getOffset());
    // 将limit添加到CacheKey对象中
    cacheKey.update(rowBounds.getLimit());
    // 将Sql 添加到CacheKey对象中
    cacheKey.update(boundSql.getSql());

    // 获取参数的映射
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();

    /* 获取用户传入的实参，并添加到CacheKey对象中 */
    // mimic DefaultParameterHandler logic
    // 获取用户传入的实参，并添加到CacheKey对象中
    for (ParameterMapping parameterMapping : parameterMappings) {
      if (parameterMapping.getMode() != ParameterMode.OUT) {
        Object value;
        // 获取属性名称
        String propertyName = parameterMapping.getProperty();
        // 判断boundSql里面是否有可添加的参数
        if (boundSql.hasAdditionalParameter(propertyName)) {
          value = boundSql.getAdditionalParameter(propertyName);
        } else if (parameterObject == null) {
          value = null;
        }
        // 判断是否有类型处理器
        else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
          value = parameterObject;
        } else {
          MetaObject metaObject = configuration.newMetaObject(parameterObject);
          value = metaObject.getValue(propertyName);
        }
        // 将实参添加到CacheKey对象中
        cacheKey.update(value);
      }
    }
    // 如果environment的id不为空，则将其添加到CacheKey中
    if (configuration.getEnvironment() != null) {
      // issue #176
      cacheKey.update(configuration.getEnvironment().getId());
    }
    return cacheKey;
  }

  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    // 检测一级缓存中，是否缓存了CacheKey对应的对象
    return localCache.getObject(key) != null;
  }

  /**
   * 清空一级缓存、提交事务
   *
   * @param required
   * @throws SQLException
   */
  @Override
  public void commit(boolean required) throws SQLException {
    if (closed) {
      throw new ExecutorException("Cannot commit, transaction is already closed");
    }
    /* 1、清空一级缓存（说白了，就是清空2个PerpetualCache中的map，清空2个map） */
    clearLocalCache();

    /* 2、刷新语句，Batch用 */
    flushStatements();

    /* 3、提交事务 */
    if (required) {
      transaction.commit();
    }

  }

  /**
   * 1、清空一级缓存
   * 2、刷新Statements（一般什么都没做）
   * 3、根据需要回滚事务
   */
  @Override
  public void rollback(boolean required) throws SQLException {
    if (!closed) {
      try {
        /* 1、清空一级缓存 */
        clearLocalCache();

        /* 2、刷新Statements（一般什么都没做） */
        flushStatements(true);
      } finally {

        /* 3、⚠️根据需要回滚事务 */
        if (required) {
          transaction.rollback();
        }
      }
    }
  }

  /**
   * 清空一级缓存，说白了就是清空map
   */
  @Override
  public void clearLocalCache() {
    // 判断当前Executor是否关闭
    if (!closed) {
      /* 当前Executor未关闭 */

      // 清空一级缓存，说白了就是清空map
      localCache.clear();

      // 清空一级缓存，说白了就是清空map
      localOutputParameterCache.clear();
    }
  }

  protected abstract int doUpdate(MappedStatement ms, Object parameter) throws SQLException;

  protected abstract List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException;

  protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
    throws SQLException;

  protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql)
    throws SQLException;

  protected void closeStatement(Statement statement) {
    if (statement != null) {
      try {
        statement.close();
      } catch (SQLException e) {
        // ignore
      }
    }
  }

  /**
   * Apply a transaction timeout.
   *
   * @param statement a current statement
   * @throws SQLException if a database access error occurs, this method is called on a closed <code>Statement</code>
   * @see StatementUtil#applyTransactionTimeout(Statement, Integer, Integer)
   * @since 3.4.0
   */
  protected void applyTransactionTimeout(Statement statement) throws SQLException {
    StatementUtil.applyTransactionTimeout(statement, statement.getQueryTimeout(), transaction.getTimeout());
  }

  private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter, BoundSql boundSql) {
    if (ms.getStatementType() == StatementType.CALLABLE) {
      final Object cachedParameter = localOutputParameterCache.getObject(key);
      if (cachedParameter != null && parameter != null) {
        final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
        final MetaObject metaParameter = configuration.newMetaObject(parameter);
        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
          if (parameterMapping.getMode() != ParameterMode.IN) {
            final String parameterName = parameterMapping.getProperty();
            final Object cachedValue = metaCachedParameter.getValue(parameterName);
            metaParameter.setValue(parameterName, cachedValue);
          }
        }
      }
    }
  }

  /**
   * 从数据库查
   *
   * @param ms            MappedStatement
   * @param parameter     "参数名"与"实参(入参对象)"之间的对应关系，方便后面填入sql语句中
   * @param rowBounds     分页相关
   * @param resultHandler resultHandler
   * @param key           CacheKey
   * @param boundSql      里面具备jdbc可执行sql，和sql参数映射
   */
  private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list;
    /* 1、⚠️在一级缓存中添加占位符 */
    localCache.putObject(key, EXECUTION_PLACEHOLDER/* 执行占位符 */);

    /* 2、⚠️执行数据库查询，并返回结果 */
    try {
      // SimpleExecutor
      list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
    }
    /* 3、删除一级缓存中的执行占位符 */ finally {
      localCache.removeObject(key);
    }

    /* 4、将查询到的结果，添加到一级缓存中 */
    // 注意：⚠️这个是用于缓存"查询到的结果，所对应的映射对象"的一级缓存
    // 题外：从这里可以看出，一级缓存是直接存储数据
    localCache.putObject(key, list);

    /* 5、如果是存储过程的话，则缓存输出参数到一级缓存中 */
    // 是否有存储过程的调用
    if (ms.getStatementType() == StatementType.CALLABLE) {
      // 缓存输出类型的参数
      // 注意：⚠️这个是用于缓存"输出参数"的一级缓存
      localOutputParameterCache.putObject(key, parameter);
    }

    return list;
  }

  /**
   * 获取数据库连接
   *
   * @param statementLog
   * @return
   * @throws SQLException
   */
  protected Connection getConnection(Log statementLog) throws SQLException {
    // 通过事务对象，获取数据库连接
    // 题外：内部是通过数据源获取连接
    // JdbcTransaction
    Connection connection = transaction.getConnection();

    // 打印日志
    if (statementLog.isDebugEnabled()) {
      // 如果需要打印Connection的日志，返回一个ConnectionLogger（代理模式，AOP思想）
      return ConnectionLogger.newInstance(connection, statementLog, queryStack);
    } else {
      return connection;
    }
  }

  @Override
  public void setExecutorWrapper(Executor wrapper) {
    this.wrapper = wrapper;
  }

  private static class DeferredLoad {

    // ⚠️结果对象对应的MetaObject，可用于设置属性
    private final MetaObject resultObject;
    // 结果对象的属性名
    private final String property;
    private final Class<?> targetType;
    private final CacheKey key;
    // 一级缓存
    private final PerpetualCache localCache;
    private final ObjectFactory objectFactory;
    private final ResultExtractor resultExtractor;

    // issue #781
    public DeferredLoad(MetaObject resultObject,
                        String property,
                        CacheKey key,
                        PerpetualCache localCache,
                        Configuration configuration,
                        Class<?> targetType) {
      // 结果对象对应的MetaObject，可用于设置属性
      this.resultObject = resultObject;
      this.property = property;
      // CacheKey
      this.key = key;
      this.localCache = localCache;
      this.objectFactory = configuration.getObjectFactory();
      this.resultExtractor = new ResultExtractor(configuration, objectFactory);
      this.targetType = targetType;
    }

    /**
     * 判断一级缓存中是否存在数据
     */
    public boolean canLoad() {
      // 一级缓存中存在数据 && 存在的数据不是"执行占位符"
      return localCache.getObject(key) != null && localCache.getObject(key) != EXECUTION_PLACEHOLDER/* 执行占位符 */;
    }

    /**
     * 从一级缓存中获取数据，作为属性值，设置到结果对象中
     */
    public void load() {
      /* 1、从一级缓存中获取数据，作为属性值 */
      // 题外：一级缓存中存放的是对象
      @SuppressWarnings("unchecked")
      // we suppose we get back a List —— 我们假设我们得到一个列表
      List<Object> list = (List<Object>) localCache.getObject(key);
      Object value = resultExtractor/* 结果提取器 */.extractObjectFromList(list, targetType);

      /* 2、⚠️往结果对象中设置属性 */
      resultObject.setValue(property, value);
    }

  }

}
