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

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.binding.MapperProxyFactory;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.result.DefaultMapResultHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * The default implementation for {@link SqlSession}.
 * Note that this class is not Thread-Safe.
 *
 * @author Clinton Begin
 */
public class DefaultSqlSession implements SqlSession {

  private final Configuration configuration;

  // 执行器
  private final Executor executor;

  // 自动提交（true：开启自动提交；false：关闭自动提交）
  private final boolean autoCommit;

  // 当前sql语句操作，是否会污染数据（true：代表数据已经被污染，是脏数据了；false：不是脏数据，是正常数据，默认为false）
  // 题外：只有在update()——CUD的时候，才会修改为true
  private boolean dirty/* 肮脏 */;

  private List<Cursor<?>> cursorList;

  public DefaultSqlSession(Configuration configuration, Executor executor, boolean autoCommit) {
    this.configuration = configuration;
    this.executor = executor;
    this.dirty = false;
    this.autoCommit = autoCommit;
  }

  public DefaultSqlSession(Configuration configuration, Executor executor) {
    this(configuration, executor, false);
  }

  @Override
  public <T> T selectOne(String statement) {
    return this.selectOne(statement, null);
  }

  /**
   * @param statement statementId，sql语句的唯一标识
   * @param parameter "参数名"与"实参(入参对象)"之间的对应关系，方便后面填入sql语句中
   * @param <T>
   * @return
   */
  @Override
  public <T> T selectOne(String statement, Object parameter) {
    // Popular vote was to return null on 0 results and throw exception on too many. —— 普遍投票是对0个结果返回null，并在太多结果上抛出异常

    /* 1、查询 */
    /**
     * 1、题外：虽然查询的是单条记录，但是用的依然是selectList()，如果得到0条则返回null，得到1条则返回1条，得到多条则报TooManyResultsException错误。
     *
     * 2、注意：由于当没有查询到结果的时候，返回的是null，因此一般建议在mapper中编写resultType的时候使用包装类型，而不是基本类型！
     * >>> 比如推荐使用Integer而不是int。这样就可以避免NPE（NullPointException）
     */
    List<T> list = this.selectList(statement, parameter);

    /* 2、一条结果，就返回这一条结果 */
    if (list.size() == 1) {
      return list.get(0);
    }
    /* 3、结果大于1，则抛出错误 */
    else if (list.size() > 1) {
      throw new TooManyResultsException/* 太多结果异常 */("Expected one result (or null) to be returned by selectOne(), but found: " + list.size());
    }
    /* 4、没有结果，则返回null */
    else {
      return null;
    }

  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
    return this.selectMap(statement, null, mapKey, RowBounds.DEFAULT);
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
    return this.selectMap(statement, parameter, mapKey, RowBounds.DEFAULT);
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
    final List<? extends V> list = selectList(statement, parameter, rowBounds);
    final DefaultMapResultHandler<K, V> mapResultHandler = new DefaultMapResultHandler<>(mapKey,
      configuration.getObjectFactory(), configuration.getObjectWrapperFactory(), configuration.getReflectorFactory());
    final DefaultResultContext<V> context = new DefaultResultContext<>();
    for (V o : list) {
      context.nextResultObject(o);
      mapResultHandler.handleResult(context);
    }
    return mapResultHandler.getMappedResults();
  }

  @Override
  public <T> Cursor<T> selectCursor(String statement) {
    return selectCursor(statement, null);
  }

  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter) {
    return selectCursor(statement, parameter, RowBounds.DEFAULT);
  }

  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds) {
    try {
      MappedStatement ms = configuration.getMappedStatement(statement);
      Cursor<T> cursor = executor.queryCursor(ms, wrapCollection(parameter), rowBounds);
      registerCursor(cursor);
      return cursor;
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public <E> List<E> selectList(String statement) {
    return this.selectList(statement, null);
  }

  /**
   * @param statement statementId，sql语句的唯一标识
   * @param parameter "参数名"与"实参(入参对象)"之间的对应关系，方便后面填入sql语句中
   */
  @Override
  public <E> List<E> selectList(String statement, Object parameter) {
    return this.selectList(statement, parameter, RowBounds.DEFAULT);
  }

  /**
   * @param statement statementId，sql语句的唯一标识
   * @param parameter "参数名"与"实参(入参对象)"之间的对应关系，方便后面填入sql语句中
   * @param rowBounds 分页相关
   */
  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
    return selectList(statement, parameter, rowBounds, Executor.NO_RESULT_HANDLER/* null */);
  }

  /**
   * 核心selectList()，其余select()，最终都是调用这个select()
   *
   * @param statement statementId，sql语句唯一标识 = 接口全限定名+方法名，例如：com.msb.mybatis_02.dao.UserDao.getUser
   * @param parameter "参数名"与"实参(入参对象)"之间的对应关系，方便后面填入sql语句中
   * @param rowBounds 行边界（分页用的）
   * @param handler   传入方法中的ResultHandler对象
   * @param <E>
   * @return
   */
  private <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    try {

      /* 1、根据statementId获取到对应的MappedStatement */
      /**
       * 题外：MappedStatement里面包含处理sql语句相关的一切信息，例如：sql语句、resultMap、执行sql语句的对象类型、sql命令类型
       */
      MappedStatement ms = configuration.getMappedStatement(statement);

      /* 2、⚠️调用Executor(执行器)，来查询结果 */
      // CachingExecutor
      return executor.query(ms, wrapCollection(parameter), rowBounds, handler);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  /**
   * @param statement sql语句唯一标识
   * @param parameter sql语句参数值
   * @param handler   传入方法中的ResultHandler对象
   */
  @Override
  public void select(String statement, Object parameter, ResultHandler handler) {
    select(statement, parameter, RowBounds.DEFAULT, handler);
  }

  @Override
  public void select(String statement, ResultHandler handler) {
    select(statement, null, RowBounds.DEFAULT, handler);
  }

  @Override
  public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    selectList(statement, parameter, rowBounds, handler);
  }

  @Override
  public int insert(String statement) {
    return insert(statement, null);
  }

  @Override
  public int insert(String statement, Object parameter) {
    return update(statement, parameter);
  }

  @Override
  public int update(String statement) {
    return update(statement, null);
  }

  @Override
  public int update(String statement, Object parameter) {
    try {
      // 因为是修改操作，代表数据已经被污染，是脏数据了
      dirty = true;
      MappedStatement ms = configuration.getMappedStatement(statement);
      return executor.update(ms, wrapCollection(parameter));
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error updating database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public int delete(String statement) {
    return update(statement, null);
  }

  @Override
  public int delete(String statement, Object parameter) {
    return update(statement, parameter);
  }

  /**
   * （1）清空一级缓存
   * （2）遍历当前SqlSession中操作过的的二级缓存，对每个二级缓存进行操作：
   *  >>> (1)如果当前二级缓存被标识为commit时需要清空，则清空当前二级缓存中的数据
   *  >>> (2)接着，将"commit时需要往二级缓存中添加的数据"，缓存到二级缓存
   *  >>> 题外：也就是说，上面清空完毕二级缓存，下面又接着添加数据，如果有的话
   *  >>> (3)重置当前二级缓存对应的TransactionalCache为刚创建时的样子
   */
  @Override
  public void commit() {
    commit(false);
  }

  @Override
  public void commit(boolean force) {
    try {
      executor.commit(isCommitOrRollbackRequired(force));
      // 把数据标识为正常。目的是为了，在后续执行rollback()、close()方法时不回滚！
      dirty = false;
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error committing transaction.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public void rollback() {
    rollback(false);
  }

  @Override
  public void rollback(boolean force) {
    try {
      executor.rollback(isCommitOrRollbackRequired(force));
      // 把数据标识为正常。目的是为了，在后续执行close()方法时，表示，这是正常数据，不需要回滚；在commit()时表示，这是正常数据，不需要提交。
      dirty = false;
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error rolling back transaction.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public List<BatchResult> flushStatements() {
    try {
      return executor.flushStatements();
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error flushing statements.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public void close() {
    try {
      /* 1、调用Executor.close()：清空 */
      executor.close(isCommitOrRollbackRequired/* 检测是需要提交还是回滚(true：回滚，false：提交) */(false));

      /* 2、关闭游标 */
      closeCursors();

      // 表示是正常数据了！后面在调用rollback()时，不需要回滚；commit()时不需要提交。
      dirty = false;
    } finally {
      ErrorContext.instance().reset();
    }
  }

  private void closeCursors() {
    if (cursorList != null && !cursorList.isEmpty()) {
      for (Cursor<?> cursor : cursorList) {
        try {
          cursor.close();
        } catch (IOException e) {
          throw ExceptionFactory.wrapException("Error closing cursor.  Cause: " + e, e);
        }
      }
      cursorList.clear();
    }
  }

  @Override
  public Configuration getConfiguration() {
    return configuration;
  }

  /**
   * 获取mapper接口的动态代理对象：
   * 1、从configuration.mapperRegistry.knownMappers中，获取mapper接口对应的MapperProxyFactory（不存在就报错）
   * 2、根据MapperProxyFactory，创建当前mapper接口对应的动态代理对象（InvocationHandler是MapperProxy，里面包含：SqlSession,mapper接口Class,methodCache）
   */
  @Override
  public <T> T getMapper(Class<T> type) {
    return configuration.getMapper(type, this);
  }

  @Override
  public Connection getConnection() {
    try {
      return executor.getTransaction().getConnection();
    } catch (SQLException e) {
      throw ExceptionFactory.wrapException("Error getting a new connection.  Cause: " + e, e);
    }
  }

  /**
   * 清空一级缓存
   */
  @Override
  public void clearCache() {
    // 清空一级缓存
    executor.clearLocalCache();
  }

  private <T> void registerCursor(Cursor<T> cursor) {
    if (cursorList == null) {
      cursorList = new ArrayList<>();
    }
    cursorList.add(cursor);
  }

  /**
   * 检测是需要提交还是回滚
   *
   * 对于sqlSession.commit()来说：true：提交，false：不提交
   * 对于sqlSession.rollback()来说：true：回滚，false：不回滚
   * 对于sqlSession.close()来说：true：回滚、失效当前sqlSession中准备往二级缓存中放入的数据，false：不回滚、生效当前sqlSession中准备往二级缓存中放入的数据
   *
   * @param force     强制提交或者回滚（默认false，代表提交）
   */
  private boolean isCommitOrRollbackRequired/* 是否需要提交或回滚 */(boolean force) {
    // 【 (关闭自动提交 && 当前操作已经污染数据为脏数据了) || force=true】则返回true，代表需要回滚

    // 只要执行update()——CUD操作，那么dirty=true，且默认情况下，为关闭自动提交，所以在关闭自动提交的情况下，又执行了CUD操作弄脏了数据库的数据，所以为true，意思说回滚，保证数据正常！
    // 从"只要执行update()——CUD操作，那么dirty=true"，可以看出，只要不主动执行sqlSession.commit()，那么数据一定是回滚，这也就要求了，如果我们要想提交数据，必须执行sqlSessionCommit()！
    return (!autoCommit && dirty) || force;
  }

  /**
   * 如果sql参数值对象是Collection或者是数组，则用ParamMap包装一下；否则，什么都不做，则原生返回"sql参数值对象"；
   *
   * @param object sql参数值对象
   */
  private Object wrapCollection(final Object object) {
    return ParamNameResolver.wrapToMapIfCollection(object, null);
  }

  /**
   * 严格的Map，如果找不到对应的key，直接地BindingException例外，而不是返国null
   *
   * @deprecated Since 3.5.5
   */
  @Deprecated
  public static class StrictMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -5741767162221585340L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + this.keySet());
      }
      return super.get(key);
    }

  }

}
