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
package org.apache.ibatis.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * 映射语句：
 * 由某个sql标签（<insert>、<delete>、<update>、<select>、<selectKey>），或者是某个sql注解(@Insert、@Delete、@Update、@Select)中的信息内容，构建而成的。
 * 也就是说：⚠️一个sql标签，或者sql注解，对应一个MappedStatement
 *
 * 题外：Statement怎么理解？直接立即为存放sql语句的即可！
 *
 * @author Clinton Begin
 */
public final class MappedStatement/* 映射语句 */ {

  // 映射文件的位置（mapper文件的位置）（也就是：dao.xml文件的位置 / mapper接口路径）
  // 例如：com/msb/mybatis_02/dao/UserDao.xml
  // 例如：com/msb/mybatis_02/dao/UserDao
  private String resource;
  private Configuration configuration;
  // mapper接口方法唯一标识，由"mapper接口全限定名+方法名"组成，例如：com.msb.mybatis_02.dao.UserDao.getUser
  private String id;
  private Integer fetchSize;
  private Integer timeout;
  // 执行sql语句的对象类型
  private StatementType statementType;
  private ResultSetType resultSetType;
  // ⚠️sql语句（<insert>、<delete>、<update>、<select>、<selectKey>标签，或者对应注解中的一条SQL语句）
  private SqlSource sqlSource;
  // 二级缓存（mapper级别的缓存，也就是某个命名空间所对应的缓存）
  // 题外：需要配置<cache>标签/@CacheNamespace，
  // >>> 根据<cache>标签/@CacheNamespace，信息构建的缓存；然后放在了当前，每个sql标签所对应的MappedStatement.cache中
  private Cache cache;

  // 引用外部参数映射（已废弃）
  // 例如：<select parameterMap="">中的parameterMap属性
  private ParameterMap parameterMap;
  /**
   * 1、题外：这里虽然是list，但是我们日常几乎都是单ResultMap
   */
  // 结果映射
  // <select>标签中的resultMap属性值所对应的ResultMap，可以配置多个值，用逗号分割
  private List<ResultMap> resultMaps;
  // flushCache属性：是否要刷新二级缓存（默认值：当前是select操作的话，默认值为false，也就是不刷新缓存；当前操作不是select操作的话，则默认为true，代表要刷新缓存）
  // 题外：CRUD都可以配置flushCache属性
  private boolean flushCacheRequired/* 需要刷新缓存 */;
  // useCache属性：是否使用二级缓存，来缓存select操作的结果（默认值：当前是select操作的话，默认值为true，代表使用缓存；当前操作不是select操作的话，则默认值为false，代表不使用缓存）
  // 例如：<select useCache="false">
  // 注意：⚠️也只有<select>标签，才有useCache属性
  private boolean useCache;
  private boolean resultOrdered;
  // SOL命令类型
  private SqlCommandType sqlCommandType;
  private KeyGenerator keyGenerator;
  private String[] keyProperties;
  private String[] keyColumns;
  // 是否包含嵌套的resultMap
  private boolean hasNestedResultMaps;
  // 数据库id
  private String databaseId;
  private Log statementLog;
  private LanguageDriver lang;
  private String[] resultSets;

  MappedStatement() {
    // constructor disabled
  }

  public static class Builder {
    private MappedStatement mappedStatement = new MappedStatement();

    public Builder(Configuration configuration, String id, SqlSource sqlSource, SqlCommandType sqlCommandType) {
      mappedStatement.configuration = configuration;
      mappedStatement.id = id;
      mappedStatement.sqlSource = sqlSource;
      mappedStatement.statementType = StatementType.PREPARED;
      mappedStatement.resultSetType = ResultSetType.DEFAULT;
      mappedStatement.parameterMap = new ParameterMap.Builder(configuration, "defaultParameterMap", null, new ArrayList<>()).build();
      mappedStatement.resultMaps = new ArrayList<>();
      mappedStatement.sqlCommandType = sqlCommandType;
      mappedStatement.keyGenerator = configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType) ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
      String logId = id;
      if (configuration.getLogPrefix() != null) {
        logId = configuration.getLogPrefix() + id;
      }
      mappedStatement.statementLog = LogFactory.getLog(logId);
      mappedStatement.lang = configuration.getDefaultScriptingLanguageInstance();
    }

    public Builder resource(String resource) {
      // 例如：com/msb/mybatis_02/dao/AccountDao.xml
      mappedStatement.resource = resource;
      return this;
    }

    public String id() {
      return mappedStatement.id;
    }

    /**
     * 构建外部的参数映射（已废弃，忽略）
     *
     * >>> 引用外部参数集合parameterMap（已废弃）
     * >>> 例如：<select parameterMap="">中的parameterMap属性
     *
     * @param parameterMap
     * @return
     */
    public Builder parameterMap(ParameterMap parameterMap) {
      mappedStatement.parameterMap = parameterMap;
      return this;
    }

    // 保存resultMaps，同时判断，是否有嵌套的resultMap
    public Builder resultMaps(List<ResultMap> resultMaps) {
      mappedStatement.resultMaps = resultMaps;
      for (ResultMap resultMap : resultMaps) {
        mappedStatement.hasNestedResultMaps = mappedStatement.hasNestedResultMaps || resultMap.hasNestedResultMaps();
      }
      return this;
    }

    public Builder fetchSize(Integer fetchSize) {
      mappedStatement.fetchSize = fetchSize;
      return this;
    }

    public Builder timeout(Integer timeout) {
      mappedStatement.timeout = timeout;
      return this;
    }

    public Builder statementType(StatementType statementType) {
      mappedStatement.statementType = statementType;
      return this;
    }

    public Builder resultSetType(ResultSetType resultSetType) {
      mappedStatement.resultSetType = resultSetType == null ? ResultSetType.DEFAULT : resultSetType;
      return this;
    }

    /**
     * 设置当前mapper的缓存，也就是二级缓存
     *
     * @param cache
     * @return
     */
    public Builder cache(Cache cache) {
      mappedStatement.cache = cache;
      return this;
    }

    // flushCache属性：是否要刷新二级缓存（默认值：当前是select操作的话，默认值为false，也就是不刷新缓存；当前操作不是select操作的话，则默认为true，代表要刷新缓存）
    public Builder flushCacheRequired(boolean flushCacheRequired) {
      mappedStatement.flushCacheRequired = flushCacheRequired;
      return this;
    }

    // 是否要缓存select操作的结果（默认值：当前是select操作的话，默认值为true，代表使用缓存；当前操作不是select操作的话，则默认值为false，代表不使用缓存）
    // 注意：⚠️也只有<select>标签，才有useCache属性
    public Builder useCache(boolean useCache) {
      mappedStatement.useCache = useCache;
      return this;
    }

    public Builder resultOrdered(boolean resultOrdered) {
      mappedStatement.resultOrdered = resultOrdered;
      return this;
    }

    public Builder keyGenerator(KeyGenerator keyGenerator) {
      mappedStatement.keyGenerator = keyGenerator;
      return this;
    }

    public Builder keyProperty(String keyProperty) {
      mappedStatement.keyProperties = delimitedStringToArray(keyProperty);
      return this;
    }

    public Builder keyColumn(String keyColumn) {
      mappedStatement.keyColumns = delimitedStringToArray(keyColumn);
      return this;
    }

    public Builder databaseId(String databaseId) {
      mappedStatement.databaseId = databaseId;
      return this;
    }

    public Builder lang(LanguageDriver driver) {
      mappedStatement.lang = driver;
      return this;
    }

    public Builder resultSets(String resultSet) {
      // 逗号分割为数组
      mappedStatement.resultSets = delimitedStringToArray/* 分隔字符串到数组 */(resultSet);
      return this;
    }

    /**
     * Resul sets.
     *
     * @param resultSet
     *          the result set
     * @return the builder
     * @deprecated Use {@link #resultSets}
     */
    @Deprecated
    public Builder resulSets(String resultSet) {
      mappedStatement.resultSets = delimitedStringToArray(resultSet);
      return this;
    }

    public MappedStatement build() {
      assert mappedStatement.configuration != null;
      assert mappedStatement.id != null;
      assert mappedStatement.sqlSource != null;
      assert mappedStatement.lang != null;
      mappedStatement.resultMaps = Collections.unmodifiableList(mappedStatement.resultMaps);
      return mappedStatement;
    }
  }

  public KeyGenerator getKeyGenerator() {
    return keyGenerator;
  }

  public SqlCommandType getSqlCommandType() {
    return sqlCommandType;
  }

  public String getResource() {
    return resource;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public String getId() {
    return id;
  }

  public boolean hasNestedResultMaps() {
    return hasNestedResultMaps;
  }

  public Integer getFetchSize() {
    return fetchSize;
  }

  public Integer getTimeout() {
    return timeout;
  }

  public StatementType getStatementType() {
    return statementType;
  }

  public ResultSetType getResultSetType() {
    return resultSetType;
  }

  public SqlSource getSqlSource() {
    return sqlSource;
  }

  public ParameterMap getParameterMap() {
    return parameterMap;
  }

  public List<ResultMap> getResultMaps() {
    return resultMaps;
  }

  public Cache getCache() {
    return cache;
  }

  public boolean isFlushCacheRequired() {
    // flushCache属性：是否要刷新一/二级缓存（默认值：当前是select操作的话，默认值为false，也就是不刷新缓存；当前操作不是select操作的话，则默认为true，代表要刷新缓存）
    return flushCacheRequired;
  }

  public boolean isUseCache() {
    // useCache属性：是否使用二级缓存，来缓存select操作的结果（默认值：当前是select操作的话，默认值为true，代表使用缓存；当前操作不是select操作的话，则默认值为false，代表不使用缓存）
    return useCache;
  }

  public boolean isResultOrdered() {
    return resultOrdered;
  }

  public String getDatabaseId() {
    return databaseId;
  }

  public String[] getKeyProperties() {
    return keyProperties;
  }

  public String[] getKeyColumns() {
    return keyColumns;
  }

  public Log getStatementLog() {
    return statementLog;
  }

  public LanguageDriver getLang() {
    return lang;
  }

  public String[] getResultSets() {
    return resultSets;
  }

  /**
   * Gets the resul sets.
   *
   * @return the resul sets
   * @deprecated Use {@link #getResultSets()}
   */
  @Deprecated
  public String[] getResulSets() {
    return resultSets;
  }

  /**
   * 构建jdbc可执行sql，以及构建sql参数映射（sql参数名称，以及在参数对象中的属性类型）：
   * （1）根据参数对象，判断某些条件是否成立，然后动态组装sql
   * （2）解析动态组装好的sql，变为jdbc可执行的sql
   * （3）同时为每一个sql参数，构建sql参数映射（ParameterMapping，面保存了sql参数名和参数类型）
   * >>> 注意：里面并没有构建sql参数和参数值之前的映射，只是按顺序，相当于保存了一下sql参数名称，以及在参数对象中的属性类型（java类型）
   *
   * @param parameterObject     参数对象，里面具有"参数名"与"实参"之间的对应关系
   */
  public BoundSql getBoundSql/* 获取绑定Sql */(Object parameterObject) {

    /*

    1、干了三件事：
    （1）根据参数对象，判断某些条件是否成立，然后动态组装sql
    （2）解析动态组装好的sql，变为jdbc可执行的sql
    （3）同时为每一个sql参数，构建sql参数映射（ParameterMapping，面保存了sql参数名和参数类型）
    >>> 注意：里面并没有构建sql参数和参数值之前的映射，只是按顺序，相当于保存了一下sql参数名称，以及在参数对象中的属性类型（java类型）

     */

    /**
     * 1、如果不存在动态标签，例如：
     *
     *  <select id="getAllUser" resultMap="userMap">
     *     select * from user
     *   </select>
     *
     * 走的是：RawSqlSource
     *
     * 2、如果存在动态标签，例如：
     *
     * <select id="getUserByUser2" resultMap="getUserByUser2_userMap">
     *     select * from user
     *     <where>
     *       <if test="#{id}!=null">
     *         id = #{id}
     *       </if>
     *     </where>
     *   </select>
     *
     * 走的是：DynamicSqlSource
     */

    // 题外：sqlSource里面包含书写的sql语句
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);

    /* 2、如果解析得到jdbc可执行的sql语句之后，sql参数映射为空，则尝试使用"外部参数映射" */

    // 获取sql参数映射
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    // 如果参数映射为空
    if (parameterMappings == null || parameterMappings.isEmpty()) {
      boundSql = new BoundSql(configuration, boundSql.getSql()/* sql语句 */, parameterMap.getParameterMappings()/* 引用的外部参数映射 */, parameterObject/* 参数对象 */);
    }

    /* 3、检查参数映射中的嵌套resultMap(忽略，一般没有) */

    // check for nested result maps in parameter mappings (issue #30) —— 检查参数映射中的嵌套结果映射（问题 30）
    for (ParameterMapping pm : boundSql.getParameterMappings()) {
      // resultMapId，这里一般为null！忽略
      String rmId = pm.getResultMapId();
      if (rmId != null) {
        ResultMap rm = configuration.getResultMap(rmId);
        if (rm != null) {
          // 判断，是否有嵌套的resultMap
          hasNestedResultMaps |= rm.hasNestedResultMaps();
        }
      }
    }

    return boundSql;
  }

  /**
   * 逗号分割为数组
   */
  private static String[] delimitedStringToArray(String in) {
    if (in == null || in.trim().length() == 0) {
      return null;
    } else {
      return in.split(",");
    }
  }

}
