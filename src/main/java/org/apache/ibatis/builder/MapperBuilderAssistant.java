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
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.CacheBuilder;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMap;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * 某一个mapper文件的构建助手（构建者模式，继承BaseBuilder）
 *
 * @author Clinton Begin
 */
public class MapperBuilderAssistant/* 映射构建器助手/mapper构建助手 */ extends BaseBuilder {

  // 当前命名空间
  private String currentNamespace;
  // dao.xml文件的位置/mapper接口的位置
  private final String resource;
  // 当前mapper文件的缓存（二级缓存）
  // 有2种方式得到：1、自身定义的当前缓存；2、引用的缓存（configuration#caches中，通过命名空间，获取到的缓存）
  private Cache currentCache;

  // 未解析的缓存引用（true：代表未获取到引用缓存，false：代表已经获取到了引用缓存）
  private boolean unresolvedCacheRef; // issue #676

  public MapperBuilderAssistant(Configuration configuration, String resource) {
    super(configuration);
    ErrorContext instance = ErrorContext.instance();
    instance.resource(resource);
    this.resource = resource;
  }

  public String getCurrentNamespace() {
    return currentNamespace;
  }

  /**
   * 设置当前命名空间
   */
  public void setCurrentNamespace(String currentNamespace) {
    // 1、命名空间为null，则直接报错
    if (currentNamespace == null) {
      throw new BuilderException("The mapper element requires a namespace attribute to be specified.");
    }

    // 2、如果已经存在命名空间，且当前要放入的命名空间，与已经存在的命名空间不相等，则报错
    if (this.currentNamespace != null && !this.currentNamespace.equals(currentNamespace)) {
      throw new BuilderException("Wrong namespace. Expected '" /* 错误的命名空间。预期的 */
        + this.currentNamespace + "' but found '"/* 但发现 */ + currentNamespace + "'.");
    }

    // 3、设置命名空间
    this.currentNamespace = currentNamespace;
  }

  /**
   * 为id加上namespace前缀
   *
   * @param base
   * @param isReference
   * @return
   */
  public String applyCurrentNamespace(String base, boolean isReference) {
    if (base == null) {
      return null;
    }
    if (isReference) {
      // is it qualified with any namespace yet? —— 它是否符合任何命名空间？
      if (base.contains(".")) {
        return base;
      }
    } else {
      // is it qualified with this namespace yet? —— 它是否符合此命名空间的条件？

      // base是否是以"当前命名空间."开头，如果是，则代表包含了当前命名空间，就直接返回
      if (base.startsWith(currentNamespace + ".")) {
        return base;
      }
      // base是否包含"."，如果包含就报错
      if (base.contains(".")) {
        // 元素名称中不允许使用点，请将其从
        throw new BuilderException("Dots are not allowed in element names, please remove it from " + base);
      }
    }
    // "当前命名空间." + base
    return currentNamespace + "." + base;
  }

  /**
   * 获取引用的缓存（二级缓存，mapper级别）：
   * 从configuration.caches中，通过@CacheNamespaceRef/<cache-ref>配置的引用缓存的命名空间，去获取对应命名空间的缓存(Cache)，作为当前mapper接口的引用缓存
   *
   * @param namespace 引用缓存所在的命名空间
   */
  public Cache useCacheRef(String namespace) {
    if (namespace == null) {
      throw new BuilderException("cache-ref element requires a namespace attribute.");
    }
    try {
      // 未解析的缓存引用（true：代表未获取到引用缓存，false：代表已经获取到了引用缓存）
      unresolvedCacheRef = true;

      /* 1、从configuration.caches中，通过引用缓存的命名空间，去获取对应的命名空间的缓存，作为引用缓存 */
      Cache cache = configuration.getCache(namespace);

      /* 2、如果不存在引用的缓存，则报错IncompleteElementException */
      if (cache == null) {
        // 这个+namespace+命名空间没有缓存可以找到
        throw new IncompleteElementException("No cache for namespace '"+ namespace + "' could be found.");
      }

      /* 3、设置引用缓存 */
      currentCache/* 设置引用缓存 */ = cache;

      unresolvedCacheRef = false;
      return cache;
    } catch (IllegalArgumentException e) {
      throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.", e);
    }
  }

  /**
   * 根据<cache>标签/@CacheNamespace，为当前mapper，创建Cache对象（二级缓存，mapper级别），并添加到configuration.caches集合中
   *
   * @param typeClass
   * @param evictionClass
   * @param flushInterval
   * @param size
   * @param readWrite
   * @param blocking
   * @param props
   */
  public Cache useNewCache(Class<? extends Cache> typeClass,
                           Class<? extends Cache> evictionClass,
                           Long flushInterval,
                           Integer size,
                           boolean readWrite,
                           boolean blocking,
                           // <cache>下的子标签<property>形成的Properties
                           Properties props) {

    /* 1、创建Cache */
    // currentNamespace：当前缓存的id，就是当前dao.xml文件的命名空间
    Cache cache = new CacheBuilder(currentNamespace)
      // 缓存的类型
      .implementation(valueOrDefault(typeClass, PerpetualCache.class/* 默认值 */))
      .addDecorator(valueOrDefault(evictionClass, LruCache.class))
      .clearInterval(flushInterval)
      .size(size)
      .readWrite(readWrite)
      .blocking(blocking)
      .properties(props)
      .build();

    /*

    2、添加Cache到configuration.caches集合中

    注意：如果dao.xml中配置<cache>，然后dao接口上也配置@CacheNamespace。
    >>> 会先使用dao.xml中配置的<cache>标签信息构建一个Cache注册到configuration.caches集合中，
    >>> 后续，在解析dao接口的时候，也会使用@CacheNamespace信息构建一个Cache，但是放入到configuration.caches集合中的时候，会报错，
    >>> 因为内部逻辑会判断是否已经存在相同Cache.id的Cache，也就是相同命名空间的Cache（因为Cache.id=命名空间，或者称呼为接口全限定名也行），存在就报错！

    注意：我们注册一个Cache到configuration.caches集合中，它会保存2份数据到configuration.caches集合中。
    >>> value相同，都是同一个Cache；但是key不同，一个key是命名空间(接口全限定名)，一个key是命名空间对应的接口名称简写(接口名)

    */
    configuration.addCache(cache);

    /* 3、设置当前mapper的缓存 */
    // ⚠️设置当前的缓存
    currentCache/* 设置当前的缓存 */ = cache;

    return cache;
  }

  public ParameterMap addParameterMap(String id, Class<?> parameterClass, List<ParameterMapping> parameterMappings) {
    id = applyCurrentNamespace(id, false);
    ParameterMap parameterMap = new ParameterMap.Builder(configuration, id, parameterClass, parameterMappings).build();
    configuration.addParameterMap(parameterMap);
    return parameterMap;
  }

  public ParameterMapping buildParameterMapping(
    Class<?> parameterType,
    String property,
    Class<?> javaType,
    JdbcType jdbcType,
    String resultMap,
    ParameterMode parameterMode,
    Class<? extends TypeHandler<?>> typeHandler,
    Integer numericScale) {
    resultMap = applyCurrentNamespace(resultMap, true);

    // Class parameterType = parameterMapBuilder.type();
    Class<?> javaTypeClass = resolveParameterJavaType(parameterType, property, javaType, jdbcType);
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);

    return new ParameterMapping.Builder(configuration, property, javaTypeClass)
      .jdbcType(jdbcType)
      .resultMapId(resultMap)
      .mode(parameterMode)
      .numericScale(numericScale)
      .typeHandler(typeHandlerInstance)
      .build();
  }

  /**
   * 创建对应的ResultMap，然后添加到configuration.resultMaps中
   */
  public ResultMap addResultMap(
    String id,
    Class<?> type,
    String extend,
    Discriminator discriminator,
    List<ResultMapping> resultMappings,
    Boolean autoMapping) {

    // ⚠️为<resultMap>的id属性，添加上当前dao.xml的命名空间
    id = applyCurrentNamespace(id, false);

    extend = applyCurrentNamespace(extend, true);

    /* 1、处理继承的resultMap */
    if (extend != null) {

      // 判断configuration.resultMaps中，是否存在extend的resultMap，如果不存在，则报错：IncompleteElementException
      // 题外：报错的目的，是为了留在后面进行处理
      if (!configuration.hasResultMap(extend)) {
        throw new IncompleteElementException("Could not find a parent resultmap with id '" + extend + "'");
      }

      // 获取继承的ResultMap
      ResultMap resultMap = configuration.getResultMap(extend);
      // 获取继承的ResultMapping集合
      List<ResultMapping> extendedResultMappings = new ArrayList<>(resultMap.getResultMappings());
      // 去重操作
      extendedResultMappings.removeAll(resultMappings);

      /* 如果此resultMap声明了构造函数，则删除父构造函数。 */
      // Remove parent constructor if this resultMap declares a constructor. —— 如果此resultMap声明了构造函数，则删除父构造函数。
      boolean declaresConstructor/* 声明构造函数 */  = false;
      for (ResultMapping resultMapping : resultMappings) {
        if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
          declaresConstructor = true;
          break;
        }
      }
      if (declaresConstructor) {
        extendedResultMappings.removeIf(resultMapping -> resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR));
      }

      // ⚠️添加继承的resultMappings
      resultMappings.addAll(extendedResultMappings);
    }

    /* 2、⚠️构建ResultMap */
    ResultMap resultMap = new ResultMap.Builder(configuration, id, type, resultMappings, autoMapping)
      .discriminator(discriminator)
      .build();

    /* 3、⚠️添加ResultMap到configuration.resultMaps */
    configuration.addResultMap(resultMap);

    return resultMap;
  }

  public Discriminator buildDiscriminator(
    Class<?> resultType,
    String column,
    Class<?> javaType,
    JdbcType jdbcType,
    Class<? extends TypeHandler<?>> typeHandler,
    Map<String, String> discriminatorMap) {
    ResultMapping resultMapping = buildResultMapping(
      resultType,
      null,
      column,
      javaType,
      jdbcType,
      null,
      null,
      null,
      null,
      typeHandler,
      new ArrayList<>(),
      null,
      null,
      false);
    Map<String, String> namespaceDiscriminatorMap = new HashMap<>();
    for (Map.Entry<String, String> e : discriminatorMap.entrySet()) {
      String resultMap = e.getValue();
      resultMap = applyCurrentNamespace(resultMap, true);
      namespaceDiscriminatorMap.put(e.getKey(), resultMap);
    }
    return new Discriminator.Builder(configuration, resultMapping, namespaceDiscriminatorMap).build();
  }

  /**
   * 用某个"sql标签"或者"sql注解"的信息，构建MappedStatement；然后注册MappedStatement到Configuration.mappedStatements中（映射语句集合）
   *
   * key（String）：MappedStatement.id = mapper接口全限定名 + 方法名 / 命名空间+标签id / 包名+类名+方法名
   * value（MappedStatement）：sql标签，或者说是sql注解信息，构建而成的MappedStatement对象
   */
  public MappedStatement addMappedStatement(
    String id,
    // 里面包含了sql语句
    SqlSource sqlSource,
    StatementType statementType,
    SqlCommandType sqlCommandType,
    Integer fetchSize,
    Integer timeout,
    // 引用外部参数集合parameterMap（已废弃）
    // 例如：<select parameterMap="">中的parameterMap属性
    String parameterMap,
    Class<?> parameterType,
    // resultMapId - <select>标签中的resultMap属性值，可以配置多个值，用逗号分割
    String resultMap,
    // resultType
    Class<?> resultType,
    ResultSetType resultSetType,

    boolean flushCache,
    boolean useCache,
    boolean resultOrdered,
    KeyGenerator keyGenerator,
    String keyProperty,
    String keyColumn,
    String databaseId,
    LanguageDriver lang,
    // resultSets属性
    String resultSets) {

    if (unresolvedCacheRef/* 未解析的缓存引用 */) {
      // Cache-ref还没有解决
      throw new IncompleteElementException("Cache-ref not yet resolved"/* 缓存引用尚未解决 */);
    }

    /*

    1、️为id加上namespace前缀，也就是mapper接口全限定名 —— 【namespace +.+ id】，例如：com.msb.mybatis_02.dao.UserDao.insert!selectKey

    注意：这里的"id"就是"MappedStatement.id"

    */
    id = applyCurrentNamespace(id, false);

    //是否是select语句
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;

    /*

    2、用某个sql标签，或者sql注解的信息，构建MappedStatement

    注意：⚠️一个sql标签，或者sql注解，对应一个MappedStatement

    */
    MappedStatement.Builder statementBuilder = new MappedStatement.Builder(configuration, id, sqlSource, sqlCommandType)
      .resource(resource)
      .fetchSize(fetchSize)
      .timeout(timeout)
      .statementType(statementType)
      .keyGenerator(keyGenerator)
      .keyProperty(keyProperty)
      .keyColumn(keyColumn)
      .databaseId(databaseId)
      .lang(lang)
      .resultOrdered(resultOrdered)
      // resultSets属性
      .resultSets(resultSets)
      // 获取resultMaps（映射结果集合）
      // 也就是获取：获取<select>标签中的resultMap属性值所对应的ResultMap，可以配置多个值，用逗号分割
      .resultMaps(getStatementResultMaps(resultMap, resultType, id))
      .resultSetType(resultSetType)
      // flushCache属性：是否要刷新二级缓存（默认值：当前是select操作的话，默认值为false，也就是不刷新缓存；当前操作不是select操作的话，则默认为true，代表要刷新缓存）
      .flushCacheRequired(valueOrDefault/* 值或默认值 */(flushCache, !isSelect))
      // 是否要缓存select操作的结果（默认值：当前是select操作的话，默认值为true，代表使用缓存；当前操作不是select操作的话，则默认值为false，代表不使用缓存）
      // 注意：⚠️也只有<select>标签，才有useCache属性
      .useCache(valueOrDefault(useCache, isSelect))
      // 设置当前mapper的缓存（二级缓存）
      .cache(currentCache);

    /* 构建外部的参数映射（已废弃，忽略） */
    ParameterMap statementParameterMap = getStatementParameterMap(parameterMap, parameterType, id);
    if (statementParameterMap != null) {
      statementBuilder.parameterMap(statementParameterMap);
    }

    // <insert>、<delete>、<update>、<select>等标签信息，构建为MappedStatement，里面包含了sql语句
    // 或者说是@Select、@Delete、@Insert、@Update等注解里面的信息，构建为MappedStatement，里面包含了sql语句
    MappedStatement statement = statementBuilder.build();

    /*

    3、注册MappedStatement到Configuration.mappedStatements中（映射语句集合）

    key：MappedStatement.id = mapper接口全限定名 + 方法名
    value：sql标签，或者说是sql注解信息，构建而成的MappedStatement对象

    */
    // ⚠️将"sql标签id"与对应的MappedStatement映射起来，存入configuration中的映射语句集合中
    configuration.addMappedStatement(statement);

    return statement;
  }

  /**
   * Backward compatibility signature 'addMappedStatement'.
   *
   * @param id             the id
   * @param sqlSource      the sql source
   * @param statementType  the statement type
   * @param sqlCommandType the sql command type
   * @param fetchSize      the fetch size
   * @param timeout        the timeout
   * @param parameterMap   the parameter map
   * @param parameterType  the parameter type
   * @param resultMap      the result map
   * @param resultType     the result type
   * @param resultSetType  the result set type
   * @param flushCache     the flush cache
   * @param useCache       the use cache
   * @param resultOrdered  the result ordered
   * @param keyGenerator   the key generator
   * @param keyProperty    the key property
   * @param keyColumn      the key column
   * @param databaseId     the database id
   * @param lang           the lang
   * @return the mapped statement
   */
  public MappedStatement addMappedStatement(String id, SqlSource sqlSource, StatementType statementType,
                                            SqlCommandType sqlCommandType, Integer fetchSize, Integer timeout, String parameterMap, Class<?> parameterType,
                                            String resultMap, Class<?> resultType, ResultSetType resultSetType, boolean flushCache, boolean useCache,
                                            boolean resultOrdered, KeyGenerator keyGenerator, String keyProperty, String keyColumn, String databaseId,
                                            LanguageDriver lang) {
    return addMappedStatement(
      id, sqlSource, statementType, sqlCommandType, fetchSize, timeout,
      parameterMap, parameterType, resultMap, resultType, resultSetType,
      flushCache, useCache, resultOrdered, keyGenerator, keyProperty,
      keyColumn, databaseId, lang, null);
  }

  /**
   * 如果存在value，则使用value；否则使用defaultValue
   */
  private <T> T valueOrDefault/* 值或默认值 */(T value, T defaultValue) {
    return value == null ? defaultValue : value;
  }

  private ParameterMap getStatementParameterMap(
    // 引用外部参数集合parameterMap（已废弃）
    // 例如：<select parameterMap="">中的parameterMap属性
    String parameterMapName,
    Class<?> parameterTypeClass,
    String statementId) {

    parameterMapName = applyCurrentNamespace(parameterMapName, true);

    // 注意
    ParameterMap parameterMap = null;
    if (parameterMapName != null) {
      try {
        parameterMap = configuration.getParameterMap(parameterMapName);
      } catch (IllegalArgumentException e) {
        throw new IncompleteElementException("Could not find parameter map " + parameterMapName, e);
      }
    } else if (parameterTypeClass != null) {
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      parameterMap = new ParameterMap.Builder(
        configuration,
        statementId + "-Inline",
        parameterTypeClass,
        parameterMappings).build();

    }
    return parameterMap;

  }

  /**
   * 从configuration.resultMaps中获取，<select resultMap="">标签中引用的ResultMap
   *
   * @param resultMap
   * @param resultType
   * @param statementId
   * @return
   */
  private List<ResultMap> getStatementResultMaps(
    // resultMapId —— <select>标签中的resultMap属性值，可以配置多个值，用逗号分割
    String resultMap,
    Class<?> resultType,
    String statementId) {

    /**
     * 1、例如：
     * <mapper namespace="com.msb.other.resultSets.t_02.dao.AccountDao">
     *
     *   <resultMap id="accountMap" type="com.msb.other.resultSets.t_02.entity.Account" >
     *     <id column="id" property="id"/>
     *     <result column="uid" property="uid"/>
     *   </resultMap>
     *
     *   <resultMap id="accountMap2" type="com.msb.other.resultSets.t_02.entity.Account">
     *     <result column="money" property="money"/>
     *   </resultMap>
     *
     *   <select id="findAll" resultMap="accountMap,accountMap2">
     *     select * from hm_account
     *   </select>
     *
     * </mapper>
     *
     * 我们的resultMap="accountMap,accountMap2"，
     * （1）经过applyCurrentNamespace()，resultMap=com.msb.other.resultSets.t_02.dao.AccountDao.accountMap,accountMap2
     *
     * （2）resultMap.split(",")，可以得到2个resultMapName，
     * 一个是com.msb.other.resultSets.t_02.dao.AccountDao.accountMap，一个是accountMap2
     *
     * （3）configuration.getResultMap(resultMapName.trim())
     *
     * com.msb.other.resultSets.t_02.dao.AccountDao.accountMap可以获取到对应的ResultMap，
     *
     * accountMap2也可以获取到对应的ResultMap。
     * >>> 之所以这样都能行，是因为，我们会存储一个ResultMap对象的2份数据到configuration.resultMaps中，只是key不同，一个key是【<resultMap>的id属性】，一个是【命名空间+.+<resultMap>的id属性】
     * >>> 比如这里，我们会为<resultMap id="accountMap2">，生成一个ResultMap对象，但是会存储2份数据到resultMaps中，这2份数据的ResultMap对象都一样，
     * >>> 但是key不同，有一个key叫做accountMap2；另一个key叫做com.msb.other.resultSets.t_02.dao.AccountDao.accountMap2
     * >>> 所以我们通过accountMap2，也可以获取到对应的ResultMap
     *
     * （4）我们还可以这样配置
     *
     * <select id="findAll" resultMap="com.msb.other.resultSets.t_02.dao.AccountDao.accountMap,com.msb.other.resultSets.t_02.dao.AccountDao.accountMap2">
     *   select * from hm_account
     * </select>
     *
     * 我们有了命名空间之后，applyCurrentNamespace()当作就不会再追加命名空间；
     * resultMap.split(",")之后，可以得到2个resultMapName：
     * >>> com.msb.other.resultSets.t_02.dao.AccountDao.accountMap,
     * >>> com.msb.other.resultSets.t_02.dao.AccountDao.accountMap2；
     * 通过configuration.getResultMap()同样可以获取到对应的ResultMap；
     * 而且如果是多个resultMap属性值的话，这样写比第一种写法更保险！
     */
    // 应用命名空间
    resultMap = applyCurrentNamespace(resultMap, true);

    List<ResultMap> resultMaps = new ArrayList<>();

    /* 1、先使用resultMap：如果resultMap不为空，就从configuration.resultMaps中获取<select resultMap="">标签中引用的ResultMap */
    if (resultMap != null) {
      String[] resultMapNames = resultMap.split(",");
      for (String resultMapName : resultMapNames) {
        try {
          // 题外：因为已经解析好了<resultMap>，所以可以从configuration.resultMaps中获取<select resultMap="">标签中引用的ResultMap
          resultMaps.add(configuration.getResultMap(resultMapName.trim()));
        } catch (IllegalArgumentException e) {
          throw new IncompleteElementException("Could not find result map '" + resultMapName + "' referenced from '" + statementId + "'", e);
        }
      }
    }

    /* 2、如果resultMap为空，再使用resultType，通过resultType构建一个resultMap */
    else if (resultType != null) {
      ResultMap inlineResultMap = new ResultMap.Builder(
        configuration,
        statementId + "-Inline",
        resultType,
        new ArrayList<>(),
        null).build();
      resultMaps.add(inlineResultMap);
    }

    /* 3、如果既不存在resultMap，也不存在resultType，那么MapperStatement的resultMaps就为空 */

    return resultMaps;
  }

  /**
   * 构建ResultMapping
   */
  public ResultMapping buildResultMapping(
    Class<?> resultType,    // <resultMap type="">结果映射类型
    String property,
    String column,
    Class<?> javaType,
    JdbcType jdbcType,
    // select属性
    String nestedSelect,
    // resultMap属性（嵌套的resultMap），
    // 例如：<collection resultMap="role"/>中的resultMap
    String nestedResultMap,
    String notNullColumn,
    String columnPrefix,
    // typeHandler属性
    Class<? extends TypeHandler<?>> typeHandler,
    List<ResultFlag> flags,
    String resultSet,
    String foreignColumn,
    boolean lazy) {

    /**
     * 例如：User里面有name属性，是String类型，那么就会返回String类型；有role属性，是Role类型，就会返回Role类型
     */
    // 获取属性类型（属性在结果类型中对应的类型）：
    // 1、未指定java类型，且属性名不为空，则获取该属性对应的set方法的入参类型作为"属性的类型"
    // 2、指定了java类型，就返回java类型
    // 3、未指定java类型，且属性名为空，则返回Object
    Class<?> javaTypeClass = resolveResultJavaType(resultType, property, javaType);

    // 类型处理器
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);

    /* 解析复合列名 */
    List<ResultMapping> composites;
    // 不存在select属性，以及不存在foreignColumn属性，则复合列名集合为null
    if ((nestedSelect == null || nestedSelect.isEmpty()) && (foreignColumn == null || foreignColumn.isEmpty())) {
      composites = Collections.emptyList();
    }
    // 存在select属性，或者存在foreignColumn属性，则解析复合列名
    else {
      composites = parseCompositeColumnName/* 解析复合列名 */(column);
    }

    // 构建ResultMapping
    return new ResultMapping.Builder(configuration, property, column, javaTypeClass)
      .jdbcType(jdbcType)
      // ⚠️给nestedSelect加上命名空间前缀
      // 例如：nestedSelect为<collection select="com.msb.mybatis_02.dao.RoleDao.getRoleByUserId"/>中的select，也就是com.msb.mybatis_02.dao.RoleDao.getRoleByUserId
      // 命名空间为com.msb.mybatis_02.dao.UserDao，由于com.msb.mybatis_02.dao.RoleDao.getRoleByUserId中包含"."，所以不会拼接命名空间前缀，而是直接使用com.msb.mybatis_02.dao.RoleDao.getRoleByUserId
      .nestedQueryId(applyCurrentNamespace(nestedSelect, true))
      // ⚠️给nestedResultMap加上命名空间前缀
      // 例如：nestedResultMap为<collection resultMap="role"/>中的resultMap，也就是role
      // >>> 命名空间为com.msb.mybatis_02.dao.UserDao，则拼接在一起就是：com.msb.mybatis_02.dao.UserDao.role
      .nestedResultMapId(applyCurrentNamespace(nestedResultMap, true))
      .resultSet(resultSet)
      // ⚠️
      .typeHandler(typeHandlerInstance)
      .flags(flags == null ? new ArrayList<>() : flags)
      .composites(composites)
      .notNullColumns(parseMultipleColumnNames(notNullColumn))
      .columnPrefix(columnPrefix)
      .foreignColumn(foreignColumn)
      .lazy(lazy)
      .build();
  }

  /**
   * Backward compatibility signature 'buildResultMapping'.
   *
   * @param resultType      the result type
   * @param property        the property
   * @param column          the column
   * @param javaType        the java type
   * @param jdbcType        the jdbc type
   * @param nestedSelect    the nested select
   * @param nestedResultMap the nested result map
   * @param notNullColumn   the not null column
   * @param columnPrefix    the column prefix
   * @param typeHandler     the type handler
   * @param flags           the flags
   * @return the result mapping
   */
  public ResultMapping buildResultMapping(Class<?> resultType, String property, String column, Class<?> javaType,
                                          JdbcType jdbcType, String nestedSelect, String nestedResultMap, String notNullColumn, String columnPrefix,
                                          Class<? extends TypeHandler<?>> typeHandler, List<ResultFlag> flags) {
    return buildResultMapping(
      resultType, property, column, javaType, jdbcType, nestedSelect,
      nestedResultMap, notNullColumn, columnPrefix, typeHandler, flags, null, null, configuration.isLazyLoadingEnabled());
  }

  /**
   * Gets the language driver.
   *
   * @param langClass the lang class
   * @return the language driver
   * @deprecated Use {@link Configuration#getLanguageDriver(Class)}
   */
  @Deprecated
  public LanguageDriver getLanguageDriver(Class<? extends LanguageDriver> langClass) {
    return configuration.getLanguageDriver(langClass);
  }

  private Set<String> parseMultipleColumnNames(String columnName) {
    Set<String> columns = new HashSet<>();
    if (columnName != null) {
      if (columnName.indexOf(',') > -1) {
        StringTokenizer parser = new StringTokenizer(columnName, "{}, ", false);
        while (parser.hasMoreTokens()) {
          String column = parser.nextToken();
          columns.add(column);
        }
      } else {
        columns.add(columnName);
      }
    }
    return columns;
  }

  /**
   * 解析复合列名，即由多个"属性名和列名"键值对组成
   *
   * 例如：column = {prop1=col1,prop2=col2}，那么得到的是property是prop1，column是col1；property是prop2，column是col2
   *
   * @param columnName      ResultMap子标签中配置的列名
   * @return
   */
  private List<ResultMapping> parseCompositeColumnName(String columnName) {
    List<ResultMapping> composites = new ArrayList<>();
    // 列名包含"="符号，或者包含","符号
    if (columnName != null && (columnName.indexOf('=') > -1 || columnName.indexOf(',') > -1)) {
      // 分割字符串
      StringTokenizer parser = new StringTokenizer(columnName, "{}=, ", false);
      /**
       * 例如：column = {prop1=col1,prop2=col2}，那么得到的是property是prop1，column是col1所构成的ResultMapping；property是prop2，column是col2所构成的ResultMapping
       */
      while (parser.hasMoreTokens()) {
        // 获取属性
        String property = parser.nextToken();
        // 获取列
        String column = parser.nextToken();
        // 构建复合的ResultMapping
        ResultMapping complexResultMapping = new ResultMapping.Builder(
          configuration, property, column, configuration.getTypeHandlerRegistry().getUnknownTypeHandler()).build();
        composites.add(complexResultMapping);
      }
    }
    return composites;
  }


  /**
   * 获取某个属性对应的类型：
   * 1、未指定java类型，且属性名不为空，则获取该属性对应的set方法的入参类型作为"属性的类型"
   * 2、指定了java类型，就返回java类型
   * 3、未指定java类型，且属性名为空，则返回Object
   *
   * 例如：User里面有name属性，是String类型，那么就会返回String类型；有role属性，是Role类型，就会返回Role类型
   *
   * @param resultType  <resultMap type="">结果映射类型
   * @param property    属性
   * @param javaType    指定的java类型
   * @return
   */
  private Class<?> resolveResultJavaType(Class<?> resultType, String property, Class<?> javaType) {
    // 1、未指定java类型，且属性名不为空，则获取该属性对应的set方法的入参类型作为"属性的类型"
    if (javaType == null && property != null) {
      try {
        // 获取返回值类型的MetaClass对象
        MetaClass metaResultType = MetaClass.forClass(resultType, configuration.getReflectorFactory());
        // 获取该属性对应的set方法的入参类型作为"属性的类型"
        javaType = metaResultType.getSetterType(property);
      } catch (Exception e) {
        // ignore, following null check statement will deal with the situation
      }
    }

    // 其余2种情况会进入到这里来：
    // 2、指定了java类型，就返回java类型
    // 3、未指定java类型，且属性名为空，则返回Object
    if (javaType == null) {
      javaType = Object.class;
    }
    return javaType;
  }

  private Class<?> resolveParameterJavaType(Class<?> resultType, String property, Class<?> javaType, JdbcType jdbcType) {
    if (javaType == null) {
      if (JdbcType.CURSOR.equals(jdbcType)) {
        javaType = java.sql.ResultSet.class;
      } else if (Map.class.isAssignableFrom(resultType)) {
        javaType = Object.class;
      } else {
        MetaClass metaResultType = MetaClass.forClass(resultType, configuration.getReflectorFactory());
        javaType = metaResultType.getGetterType(property);
      }
    }
    if (javaType == null) {
      javaType = Object.class;
    }
    return javaType;
  }

}
