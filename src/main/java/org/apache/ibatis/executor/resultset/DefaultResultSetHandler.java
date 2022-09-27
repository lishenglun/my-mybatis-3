/*
 *    Copyright 2009-2022 the original author or authors.
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
package org.apache.ibatis.executor.resultset;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.ibatis.annotations.AutomapConstructor;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.cursor.defaults.DefaultCursor;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.loader.ResultLoader;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.executor.result.DefaultResultHandler;
import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.util.MapUtil;

/**
 * 默认结果集处理器（也就是默认处理ResultSet的类）：
 *
 * 用于转化结果集resultSet，将结果集resultSets，转化成结果列表（或cursor）和处理"储存过程"的输出
 *
 * 题外：DefaultResultSetHandler是Mybatis为ResultSetHandler提供的唯一一个实现类
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Iwao AVE!
 * @author Kazuki Shimizu
 */
public class DefaultResultSetHandler implements ResultSetHandler {

  private static final Object DEFERRED/* 延迟 */ = new Object();

  // 关联的Executor、Configuration、 MappedStatement、 RowBounds对象
  // 执行器
  private final Executor executor;
  // 配置
  private final Configuration configuration;
  // 映射Statement
  private final MappedStatement mappedStatement;
  // 分页参数
  private final RowBounds rowBounds;
  // 参数处理器
  private final ParameterHandler parameterHandler;
  // 结果处理器：对映射好的结果对象，进行后置处理。一般是存储所有的行结果对象。
  // 用户指定用于处理结果集的ResultHandler对象
  private final ResultHandler<?> resultHandler;
  // 动态sql载体
  private final BoundSql boundSql;
  // 类型处理器容器
  private final TypeHandlerRegistry typeHandlerRegistry;
  // 对象工厂
  private final ObjectFactory objectFactory;
  // 反射器工厂
  private final ReflectorFactory reflectorFactory;

  // 嵌套结果
  // nested resultmaps
  private final Map<CacheKey, Object> nestedResultObjects = new HashMap<>();
  // 未知，忽略
  private final Map<String, Object> ancestorObjects = new HashMap<>();
  // 上一条行值
  private Object previousRowValue;

  // multiple resultsets —— 多个结果集

  // key：resultSet属性 —— 代表引用的结果集名称
  // value：resultSet属性所在标签的ResultMapping —— 代表引用的ResultSet名称对应的ResultSet，所采取的ResultMap，因为ResultMapping中有nestedResultMapId，可以通过nestedResultMapId，可以得到引用的ResultSet所采取的ResultMap
  // 题外：只有<collection>、<association>标签中存在resultSet属性
  private final Map<String, ResultMapping> nextResultMaps/* 下一个结果图 */ = new HashMap<>();

  private final Map<CacheKey, List<PendingRelation>> pendingRelations/* 待定关系 */ = new HashMap<>();

  // Cached Automappings —— 自动映射的缓存

  // 自动映射缓存
  // key：mapKey(映射key) = resultMap.id:columnPrefix
  // value：未映射的列名所对应的UnMappedColumnAutoMapping集合
  // >>> 题外：⚠️未映射的列，所采取的映射方式就是自动映射，所以叫UnMappedColumnAutoMapping —— 未映射的列,自动映射
  private final Map<String, List<UnMappedColumnAutoMapping>> autoMappingsCache = new HashMap<>();
  // 构造器自动映射
  // key：mapKey(映射key) = resultMap.id:columnPrefix
  // value：ResultSet中的列名
  private final Map<String, List<String>> constructorAutoMappingColumns/* 构造器自动映射列 */ = new HashMap<>();

  // 是否"使用构造器映射"的标识
  // temporary marking flag that indicate using constructor mapping (use field to reduce memory usage)
  // 指示使用构造器映射的临时标记标志（使用字段来减少内存使用）
  private boolean useConstructorMappings;

  private static class PendingRelation {

    // 单行结果对象的metaObject
    public MetaObject metaObject;
    // 单行结果对象中某个属性的ResultMapping（也就是，当前"resultSet属性""所在标签的ResultMapping"）
    public ResultMapping propertyMapping;

  }

  private static class UnMappedColumnAutoMapping {

    // 列名
    private final String column;
    // 列名在结果对象中所对应的属性名
    private final String property;
    // "结果对象中的属性类型"与"列名jdbc类型"所对应的TypeHandler
    private final TypeHandler<?> typeHandler;
    private final boolean primitive;

    public UnMappedColumnAutoMapping(String column, String property, TypeHandler<?> typeHandler, boolean primitive) {
      this.column = column;
      this.property = property;
      this.typeHandler = typeHandler;
      this.primitive = primitive;
    }
  }

  public DefaultResultSetHandler(Executor executor, MappedStatement mappedStatement, ParameterHandler parameterHandler, ResultHandler<?> resultHandler, BoundSql boundSql,
                                 RowBounds rowBounds) {
    this.executor = executor;
    this.configuration = mappedStatement.getConfiguration();
    this.mappedStatement = mappedStatement;
    this.rowBounds = rowBounds;
    this.parameterHandler = parameterHandler;
    this.boundSql = boundSql;
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();
    this.reflectorFactory = configuration.getReflectorFactory();
    this.resultHandler = resultHandler;
  }

  //
  // HANDLE OUTPUT PARAMETER
  //

  @Override
  public void handleOutputParameters(CallableStatement cs) throws SQLException {
    final Object parameterObject = parameterHandler.getParameterObject();
    final MetaObject metaParam = configuration.newMetaObject(parameterObject);
    final List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    for (int i = 0; i < parameterMappings.size(); i++) {
      final ParameterMapping parameterMapping = parameterMappings.get(i);
      if (parameterMapping.getMode() == ParameterMode.OUT || parameterMapping.getMode() == ParameterMode.INOUT) {
        if (ResultSet.class.equals(parameterMapping.getJavaType())) {
          handleRefCursorOutputParameter((ResultSet) cs.getObject(i + 1), parameterMapping, metaParam);
        } else {
          final TypeHandler<?> typeHandler = parameterMapping.getTypeHandler();
          metaParam.setValue(parameterMapping.getProperty(), typeHandler.getResult(cs, i + 1));
        }
      }
    }
  }

  // 处理游标（0UT参数）
  private void handleRefCursorOutputParameter(ResultSet rs, ParameterMapping parameterMapping, MetaObject metaParam) throws SQLException {
    if (rs == null) {
      return;
    }
    try {
      // 获取映射使用的ResultMap对象
      final String resultMapId = parameterMapping.getResultMapId();
      final ResultMap resultMap = configuration.getResultMap(resultMapId);
      final ResultSetWrapper rsw = new ResultSetWrapper(rs, configuration);
      if (this.resultHandler == null) {
        final DefaultResultHandler resultHandler = new DefaultResultHandler(objectFactory);
        handleRowValues(rsw, resultMap, resultHandler, new RowBounds(), null);
        // 将映射得到的结果对象保存到parameterObject中
        metaParam.setValue(parameterMapping.getProperty(), resultHandler.getResultList());
      } else {
        handleRowValues(rsw, resultMap, resultHandler, new RowBounds(), null);
      }
    } finally {
      // issue #228 (close resultsets)
      closeResultSet(rs);
    }
  }

  //
  // HANDLE RESULT SETS —— 处理结果集
  //

  /**
   * 处理所有ResultSet
   *
   * @param stmt
   * @return
   * @throws SQLException
   */
  @Override
  public List<Object> handleResultSets(Statement stmt) throws SQLException {
    ErrorContext.instance().activity("handling results").object(mappedStatement.getId());

    // 保存映射结果对象
    final List<Object> multipleResults = new ArrayList<>();

    // 结果集个数
    int resultSetCount = 0;

    /*

    1、获取ResultSet，然后用ResultSetWrapper包装了一下ResultSet。
    ResultSetWrapper里面保存了typeHandlerRegistry、resultSet、以及"列名、列的jdbc类型、列对应的Java类型"

    */

    /**
     * 题外：基本上只有一个ResultSet，存储过程才可能有多个ResultSet
     */
    // 获取第一个ResultSet，用ResultSetWrapper包装了一下ResultSet
    ResultSetWrapper rsw = getFirstResultSet(stmt);

    /* 2、从mappedStatement中获取配置的ResultMap集合（一般只有一个） */
    /**
     *  获取ResultMap集合，这里虽然是list，但是我们日常几乎都是单ResultMap
     */
    // 获取MappedStatement.resultMaps集合
    List<ResultMap> resultMaps = mappedStatement.getResultMaps();

    // resultMap个数
    int resultMapCount = resultMaps.size();

    // 验证：如果存在ResultSet，则必须要存在resultMap，否则报错
    validateResultMapsCount(rsw, resultMapCount);

    /* 3、遍历处理ResultSet（一般只有一个） */

    /**
     * 1、注意：⚠️resultMapCount > resultSetCount，这行代码很重要，从这里可以看出：
     *
     * （1）有多少个resultMap就遍历几个resultSet（除非resultSet的数量不够resultMap的数量，则无法遍历resultMap量次），一个resultSet对应一个resultMap。
     *
     * （2）另外resultMaps的顺序是按照<select>标签中resultMap属性值配置的顺序来的，所以：我们<select>标签中resultMap属性值顺序，与返回的ResultSet顺序一一对应。
     *
     * （3）只有处理完了ResultMap数量的ResultSet之后，剩下的ResultSet，才属于resultSets属性的！
     *
     * 例如：这里配置了2个ResultMap，只有处理完了2个ResultSet之后，剩下的ResultSet，才属于resultSets属性的！
     * >>> 如果只有1个ResultSet，那么也只会使用1个ResultMap(accountMap)，去处理这1个ResultSet；剩余的1个ResultMap(accountMap2是闲置的)；另外，没有属于resultSets属性的ResultSet
     * >>> 如果有2个ResultSet，那么也只会使用2个ResultMap(accountMap,accountMap2)，去处理这2个ResultSet；没有ResultMap闲置；另外，也没有属于resultSets属性的ResultSet
     * >>> 如果有3个ResultSet，那么也只会使用2个ResultMap(accountMap,accountMap2)，去处理这2个ResultSet；没有ResultMap闲置；同时有1个属于resultSets属性的ResultSet，这个ResultSet名称是testResultSet
     * >>> 如果有4个ResultSet，那么也只会使用2个ResultMap(accountMap,accountMap2)，去处理这2个ResultSet；没有ResultMap闲置；同时有2个属于resultSets属性的ResultSet，2个ResultSet名称，依次是testResultSet、testResultSet2
     *
     * <select id="findAll" resultMap="accountMap,accountMap2" resultSets="testResultSet,testResultSet2">
     *   select * from hm_account
     * </select>
     *
     * （4）我们"resultSets属性中配置的ResultSet的名称"；与"处理完了ResultMap数量的ResultSet之后，剩下的属于resultSets属性的ResultSet"一一对应
     *
     * 例如：有4个ResultRest，按顺序，分别是：rs_01、rs_02、rs_03、rs_04。
     * >>> 那么rs_01与accountMap对应，rs_02与accountMap2对应；
     * >>> 在处理完毕2个ResultMap数量的ResultSet之后；还剩余2个ResultSet，属于resultSets属性；
     * >>> 其中rs_03的名称是testResultSet，rs_04的名称是testResultSet2
     *
     * <select id="findAll" resultMap="accountMap,accountMap2" resultSets="testResultSet,testResultSet2">
     *   select * from hm_account
     * </select>
     *
     * 题外：<select>标签中的resultMap属性值，可以配置多个值，用逗号分割
     * 题外：只有<select>标签中，才能配置resultMap属性
     * 题外：resultSets配置的是ResultSet的名称
     */
    // 遍历处理ResultSet（一般来说只有一个，所以只会遍历一次）
    while (rsw != null && resultMapCount > resultSetCount/* 结果集个数 */) {

      /* 3.1、获取当前ResultSet对应的ResultMap（一个ResultSet对应一个ResultMap） */
      // 注意：ResultMap里面并不包含实际的数据，只是封装了映射关系
      ResultMap resultMap = resultMaps.get(resultSetCount);

      /* 3.2、️⚠️根据当前ResultSet对应的ResultMap，进行结果集映射 */
      // ⚠️处理结果集映射（在这里面进行具体结果的映射）
      // 根据当前ResultSet对应的ResultMap中定义的映射规则，对ResultSet进行结果集映射；并将映射好的结果对象，添加到multipleResult集合中保存
      handleResultSet(rsw, resultMap, multipleResults, null);

      /* 3.3、获取下一个ResultSet */
      rsw = getNextResultSet(stmt);

      /* 3.4、清空nestedResultObjects集合，也就是清空一下刚刚存在的一些嵌套结果集对象 */
      cleanUpAfterHandlingResultSet();

      // 递增resultSetCount
      resultSetCount++;
    }

    /* 4、存储过程相关代码 */

    /**
     * 1、resultSets属性作用：多结果集的情况下使用，有些语句可能执行后返回多个结果集，它为每个"resultMap数量剩余后的"结果集(ResultSet)定义一个名称，以逗号分割
     *
     * 简单概括：为每个结果集(ResultSet)定义一个名称
     *
     * 2、resultSet属性作用：书写要引用的结果集(ResultSet)的名称，通过结果集名称，引用结果集
     *
     * 3、resultSets和resultSet使用示范：
     *
     * <resultMap id="accountMap" type="com.msb.other.resultSets.t_02.entity.Account">
     *   <id column="id" property="id"/>
     *   <result column="uid" property="uid"/>
     *   <result column="money" property="money"/>
     *   <!-- 代表引用第4个ResultSet -->
     *   <association property="user" javaType="com.msb.other.resultSets.t_02.entity.User"
     *                resultSet="testResultSet2" foreignColumn="id" column="uid"></association>
     * </resultMap>
     *
     * <select id="findAll" resultMap="accountMap,accountMap2" resultSets="testResultSet,testResultSet2">
     *   select * from hm_account
     * </select>
     */
    // 获取resultSets属性值中配置的ResultSet名称集合
    String[] resultSets = mappedStatement.getResultSets();
    if (resultSets != null) {
      // 遍历resultSets属性值中配置的ResultSet名称
      while (rsw != null && resultSetCount < resultSets.length) {

        /* 4.1、获取resultSets属性值中配置的ResultSet名称，作为当前ResultSet的名称 */
        String resultSet = resultSets[resultSetCount];

        /*

        4.2、根据当前ResultSet的名称，从nextResultMaps中获取"引用了当前ResultSet名称"的标签的ResultMapping；
        然后从ResultMapping中获取到nestedResultMapId；
        通过nestedResultMapId，获取ResultMap，作为处理当前ResultSet的ResultMap

        题外：根据当前ResultSet的名称，从nextResultMaps中获取"引用了当前ResultSet名称"的标签的ResultMapping，这个ResultMapping，也就是resultSet属性所在标签的ResultMapping

        题外：这个ResultMapping，代表了当前ResultSet归属的字段；后续也可以通过这个ResultMapping获取到处理当前ResultSet的ResultMap，将处理好的结果对象，放入到ResultMapping中声明的字段里面

        */
        // 根据当前ResultSet的名称，从nextResultMaps中获取"引用了当前ResultSet"的标签的ResultMapping（标签是通过resultSet属性，配置ResultSet名称，然后引用名称对应的ResultSet）
        ResultMapping parentMapping = nextResultMaps.get(resultSet);
        if (parentMapping != null) {
          // 从ResultMapping中获取到nestedResultMapId
          String nestedResultMapId = parentMapping.getNestedResultMapId();
          // 通过nestedResultMapId，获取ResultMap，作为当前ResultSet的ResultMap
          ResultMap resultMap = configuration.getResultMap(nestedResultMapId);

          /* 4.3、⚠️根据当前ResultSet对应的ResultMap，进行结果集映射 */
          // 根据ResultMap对象映射结果集
          handleResultSet(rsw, resultMap, null, parentMapping/* ⚠️ */);
        }

        /* 4.4、获取下一个ResultSet */
        rsw = getNextResultSet(stmt);

        /* 3.4、清空nestedResultObjects集合，也就是清空一下刚刚存在的一些嵌套结果集对象 */
        cleanUpAfterHandlingResultSet();

        // 递增resultSetCount
        resultSetCount++;
      }

    }

    /**
     * 如果是单数据集，就将其展开返回
     * multipleResults的结构是List<List<Object>>
     * 这种结构是为了支持多数据集，存储过程可能返回多个数据集。
     */
    // 返回结果
    return collapseSingleResultList(multipleResults);
  }

  @Override
  public <E> Cursor<E> handleCursorResultSets(Statement stmt) throws SQLException {
    ErrorContext.instance().activity("handling cursor results").object(mappedStatement.getId());

    ResultSetWrapper rsw = getFirstResultSet(stmt);

    List<ResultMap> resultMaps = mappedStatement.getResultMaps();

    int resultMapCount = resultMaps.size();
    validateResultMapsCount(rsw, resultMapCount);
    if (resultMapCount != 1) {
      throw new ExecutorException("Cursor results cannot be mapped to multiple resultMaps");
    }

    ResultMap resultMap = resultMaps.get(0);
    return new DefaultCursor<>(this, resultMap, rsw, rowBounds);
  }

  /**
   * 通过Statement获取ResultSet，然后用ResultSetWrapper包装一下ResultSet，最后返回ResultSetWrapper
   */
  private ResultSetWrapper getFirstResultSet(Statement stmt) throws SQLException {
    /* 1、获取ResultSet（结果集） */
    ResultSet rs = stmt.getResultSet();

    while (rs == null) {
      // move forward to get the first resultset in case the driver
      // doesn't return the resultset as the first result (HSQLDB 2.1)
      // 上面的翻译：如果驱动程序没有将结果集作为第一个结果返回（HSQLDB 2.1），则继续获取第一个结果集

      // 检测是否还有待处理的ResultSet
      if (stmt.getMoreResults()) {
        rs = stmt.getResultSet();
      }
      // 没有待处理的ResultSet
      else {
        if (stmt.getUpdateCount() == -1) {
          // no more results. Must be no resultset —— 没有更多的结果。必须是没有结果集
          break;
        }
      }
    }

    /* 2、⚠️用ResultSetWrapper包装一下ResultSet，在ResultSetWrapper里面保存了typeHandlerRegistry、resultSet、以及"列名、列的jdbc类型、列对应的Java类型" */
    return rs != null ? new ResultSetWrapper(rs, configuration) : null;
  }

  private ResultSetWrapper getNextResultSet(Statement stmt) {
    // Making this method tolerant of bad JDBC drivers —— 使此方法能够容忍不良的JDBC驱动程序
    try {
      if (stmt.getConnection().getMetaData().supportsMultipleResultSets()) {
        // Crazy Standard JDBC way of determining if there are more results —— 确定是否有更多结果的疯狂标准JDBC方法
        if (!(!stmt.getMoreResults() && stmt.getUpdateCount() == -1)) {
          // 获取下一个ResultSet
          ResultSet rs = stmt.getResultSet();

          // 继续获取下一个
          if (rs == null) {
            return getNextResultSet(stmt);
          }
          // 包装成ResultSetWrapper返回
          else {
            return new ResultSetWrapper(rs, configuration);
          }
        }
      }
    } catch (Exception e) {
      // Intentionally ignored.
    }
    return null;
  }

  private void closeResultSet(ResultSet rs) {
    try {
      if (rs != null) {
        rs.close();
      }
    } catch (SQLException e) {
      // ignore
    }
  }

  private void cleanUpAfterHandlingResultSet() {
    nestedResultObjects.clear();
  }

  /**
   * 验证：如果存在ResultSet，则必须要存在resultMap，否则报错
   *
   * @param rsw
   * @param resultMapCount
   */
  private void validateResultMapsCount(ResultSetWrapper rsw, int resultMapCount) {
    // 如果存在ResultSet，则必须要存在resultMap，否则报错
    if (rsw != null && resultMapCount < 1) {
      throw new ExecutorException("A query was run and no Result Maps were found for the Mapped Statement '" + mappedStatement.getId()
        + "'.  It's likely that neither a Result Type nor a Result Map was specified.");
    }
  }

  /**
   * 处理一个ResultSet（所有行）
   *
   * @param rsw
   * @param resultMap
   * @param multipleResults
   * @param parentMapping
   * @throws SQLException
   */
  private void handleResultSet(ResultSetWrapper rsw, ResultMap resultMap, List<Object> multipleResults/* 存放所有行的结果对象 */, ResultMapping parentMapping) throws SQLException {
    try {
      /* 1、存在父映射，就采用父映射 */
      /**
       * 1、什么时候会存在parentMapping？
       *
       * 通过resultSet属性，引入ResultSet的时候，就会用"resultSet属性所在标签"的ResultMapping作为parentMapping
       *
       * 2、存在parentMapping时，ResultMap是？
       *
       * （1）resultSet属性所在标签，配置了resultMap属性，则用resultMap属性值指向的ResultMap作为当前ResultSet的ResultMap；
       * （2）resultSet属性所在标签，未配置resultMap属性，则用标签下的子标签，构建而成的ResultMap
       *
       * 题外：⚠️只有<collection>、<association>标签中存在resultSet属性
       */
      // 题外：普通sql无父Mapping
      if (parentMapping != null) {
        // ⚠️处理多结果集中的嵌套映射
        //
        handleRowValues(rsw, resultMap, null, RowBounds.DEFAULT, parentMapping);
      }
      /* 2、不存在父映射 */
      // ⚠️resultHandler：对映射好的结果对象，进行后置处理。一般是存储所有的行结果对象。
      else {
        /* 2.1、用户未指定ResultHandler */
        if (resultHandler == null) {

          /* （1）创建DefaultResultHandler，用于存储所有行映射处理好的结果对象 */
          // 题外：如果用户未指定处理ResultSet的ResultHandler，则使用默认的ResultHandler
          DefaultResultHandler/* 默认结果处理器 */ defaultResultHandler = new DefaultResultHandler(objectFactory);

          /* （2）⚠️映射处理ResultSet(结果集)中所有行数据，暂存到DefaultResultHandler中 */
          // 然后将每行数据，映射得到的结果对象，暂存到DefaultResultHandler中
          handleRowValues/* 处理行数据 */(rsw, resultMap, defaultResultHandler, rowBounds, null);

          /* （3）将DefaultResultHandler中保存的所有行的结果对象，添加到multipleResults中 */
          multipleResults.add(defaultResultHandler.getResultList());

        }
        /* 2.2、如果用户指定了ResultHandler */
        else {
          // ⚠️使用用户指定的ResultHandler对象处理ResultSet数据映射
          handleRowValues(rsw, resultMap, resultHandler, rowBounds, null);
        }
      }
    } finally {
      // issue #228 (close resultsets)
      // 调用ResultSet.close()方法关闭结果集
      closeResultSet(rsw.getResultSet());
    }
  }

  @SuppressWarnings("unchecked")
  private List<Object> collapseSingleResultList(List<Object> multipleResults) {
    return multipleResults.size() == 1 ? (List<Object>) multipleResults.get(0) : multipleResults;
  }

  //
  // HANDLE ROWS FOR SIMPLE RESULTMAP —— 处理简单结果图的行
  //

  /**
   * 处理一个ResultSet的所有行数据，进行映射
   *
   * @param rsw
   * @param resultMap
   * @param resultHandler   处理映射，以及存储映射结果
   * @param rowBounds
   * @param parentMapping
   * @throws SQLException
   */
  public void handleRowValues(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler<?> resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
    /**
     * 例如：属性与列名匹配起来
     * <resultMap id="userResultMap" type="User">
     *   <id property="id" column="user_id" />
     *   <result property="username" column="user_name"/>
     *   <result property="password" column="hashed_password"/>
     * </resultMap>
     *
     * 但是可能存在很复杂的，嵌套层级很深的结果映射，所以要对我们的嵌套结果进行更多层次的处理：
     *
     * <resultMap id="detailedBlogResultMap" type="Blog">
     *   <constructor>
     *     <idArg column="blog_id" javaType="int"/>
     *   </constructor>
     *   <result property="title" column="blog_title"/>
     *   <association property="author" javaType="Author">
     *     <id property="id" column="author_id"/>
     *     <result property="username" column="author_username"/>
     *     <result property="password" column="author_password"/>
     *     <result property="email" column="author_email"/>
     *     <result property="bio" column="author_bio"/>
     *     <result property="favouriteSection" column="author_favourite_section"/>
     *   </association>
     *   <collection property="posts" ofType="Post">
     *     <id property="id" column="post_id"/>
     *     <result property="subject" column="post_subject"/>
     *     <association property="author" javaType="Author"/>
     *     <collection property="comments" ofType="Comment">
     *       <id property="id" column="comment_id"/>
     *     </collection>
     *     <collection property="tags" ofType="Tag" >
     *       <id property="id" column="tag_id"/>
     *     </collection>
     *     <discriminator javaType="int" column="draft">
     *       <case value="1" resultType="DraftPost"/>
     *     </discriminator>
     *   </collection>
     * </resultMap>
     *
     */
    /* 1、嵌套ResultMap的处理 */
    // 判断是否有嵌套ResultMap（存在nestedResultMapId && 未配置resultSet属性）
    // 如果存在，就进行嵌套结果的处理
    if (resultMap.hasNestedResultMaps()) {
      // 检测是否允许在嵌套映射中使用RowBound
      ensureNoRowBounds/* 确保没有行界限 */();
      checkResultHandler();
      // 检测是否允许在嵌套映射中使用用户自定义的ResultHandler
      handleRowValuesForNestedResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    }
    /* 2、单一ResultMap的处理 */
    // 普通ResultMap解析
    // 单一结果的处理
    else {
      // 针对不含嵌套映射的简单映射的处理
      handleRowValuesForSimpleResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    }
  }

  private void ensureNoRowBounds() {
    if (configuration.isSafeRowBoundsEnabled()
      && rowBounds != null && (rowBounds.getLimit() < RowBounds.NO_ROW_LIMIT || rowBounds.getOffset() > RowBounds.NO_ROW_OFFSET)) {
      throw new ExecutorException("Mapped Statements with nested result mappings cannot be safely constrained by RowBounds. "
        + "Use safeRowBoundsEnabled=false setting to bypass this check.");
    }
  }

  protected void checkResultHandler() {
    if (resultHandler != null && configuration.isSafeResultHandlerEnabled() && !mappedStatement.isResultOrdered()) {
      throw new ExecutorException("Mapped Statements with nested result mappings cannot be safely used with a custom ResultHandler. "
        + "Use safeResultHandlerEnabled=false setting to bypass this check "
        + "or ensure your statement returns ordered data and set resultOrdered=true on it.");
    }
  }

  /**
   * 循环处理一个ResultSet中的每行数据
   *
   * @param rsw
   * @param resultMap
   * @param resultHandler
   * @param rowBounds
   * @param parentMapping
   * @throws SQLException
   */
  private void handleRowValuesForSimpleResultMap(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler<?> resultHandler, RowBounds rowBounds, ResultMapping parentMapping)
    throws SQLException {
    /* 1、创建DefaultResultContext */
    DefaultResultContext/* 默认结果上下文 */<Object> resultContext = new DefaultResultContext<>();

    /* 2、ResultSet */
    // 结果集
    ResultSet resultSet = rsw.getResultSet();

    /* 3、跳过分页行数 */
    // 跳过分页行数（跳过RowBounds.offset之前的行数，定位到RowBounds.offset指定的行 —— 根据分页的offset参数(根据RowBounds.offset)，跳过指定的行）
    // 题外：RowBounds是伪分页
    skipRows/* 跳过行 */(resultSet, rowBounds);

    /* 4、循环处理ResultSet中的每行数据 */
    // 判断是否可以接着往下处理结果
    // 只有当【处理的行数没有达到上限 && ResultSet没有关闭 && ResultSet中还有可以处理的处理】才能接着往下进行处理
    while (
      // 判断处理的行数，是否已经达到上限
      shouldProcessMoreRows/* 是否应该处理更多的结果行 */(resultContext, rowBounds)
      // 判断ResultSet是否已经关闭
      && !resultSet.isClosed()/* resultSet没有关闭 */
      // 判断ResultSet中是否还有可处理的数据
      // 注意：⚠️此时已经把指针往下一行进行移动了，所以如果从resultSet中获取数据，就已经是获取某一行中的数据了
      && resultSet.next()/* 结果集中有下一个结果 */) {

      /*

      4.1、处理鉴别器：
      （1）根据该行数据中的鉴别值，去鉴别器中，获取结果映射时，使用的ResultMap；
      （2）如果不存在鉴别器，或者未能从鉴别器当中获取到ResultMap，则返回原先的ResultMap

      */
      // 处理ResultMap中的鉴别器：从ResultSet中获取鉴别值，然后根据鉴别值，从鉴别器中选择出对应的映射使用的ResultMap；
      // 如果不存在鉴别器，或者未能从鉴别器当中获取到ResultMap，则返回原先的ResultMap
      ResultMap discriminatedResultMap = resolveDiscriminatedResultMap/* 解决有区别的结果图 */(resultSet, resultMap, null);

      /*

      4.2、⚠️映射处理单行数据，得到映射好的单行结果对象
      (1)先创建结果对象
      (2)字段映射，填充属性，有2种方式：
      >>> (1)自动映射，填充属性
      >>> (2)根据ResultMap中配置好的属性映射，填充属性

      */
      // ⚠️映射处理一行的值，返回映射对象 —— 根据最终确定的ResultMap对ResultSet中的一行记录进行映射处理，得到映射后的结果对象
      // 题外：这里是返回映射好的单行数据，已经将单行数据转换成javaBean的实例了
      Object rowValue = getRowValue(rsw, discriminatedResultMap, null);

      /*

      4.3、存储当前行的结果对象
      （1）如果当前是resultSet属性引入的ResultSet，那么会把当前ResultSet行结果，与原先的ResultSet行结果进行链接
      （2）否则，将映射好的，当前行结果对象，保存到resultHandler中

      */
      // storeObject方法针对返回的行值进行ResultHandler处理（默认ResultHandler就是存储起来）
      storeObject(resultHandler, resultContext, rowValue, parentMapping, resultSet);
    }

  }

  /**
   * 存储当前行的结果对象
   *
   * @param resultHandler
   * @param resultContext
   * @param rowValue
   * @param parentMapping
   * @param rs
   * @throws SQLException
   */
  private void storeObject(ResultHandler<?> resultHandler, DefaultResultContext<Object> resultContext, Object rowValue/* 单行数据结果对象 */, ResultMapping parentMapping, ResultSet rs) throws SQLException {
    /**
     * 1、什么时候会存在parentMapping？
     *
     * 通过resultSet属性，引入ResultSet的时候，就会用"resultSet属性所在标签"的ResultMapping作为parentMapping
     *
     * 2、存在parentMapping时，ResultMap是？
     *
     * （1）resultSet属性所在标签，配置了resultMap属性，则用resultMap属性值指向的ResultMap作为当前ResultSet的ResultMap；
     * （2）resultSet属性所在标签，未配置resultMap属性，则用标签下的子标签，构建而成的ResultMap
     *
     * 题外：⚠️只有<collection>、<association>标签中存在resultSet属性
     */
    /* 1、存在父映射（用于resultSet属性的时候） */
    if (parentMapping != null) {
      // 当前ResultSet行结果，与原先的ResultSet行结果进行链接（将当前ResultSet行结果对象，保存到最外层结果对象对应的属性中）
      linkToParents(rs, parentMapping, rowValue);
    }
    /* 2、不存在父映射 */
    else {
      // ⚠️将"当前行的结果对象"保存到ResultHandler中
      callResultHandler(resultHandler, resultContext, rowValue);
    }
  }

  /**
   * 存储当前行数据所对应的结果对象
   *
   * @param resultHandler           ResultHandler：存储所有行的结果对象
   * @param resultContext           DefaultResultContext：递增处理的行数据量、保存将当前行的结果对象
   * @param rowValue                单行数据结果
   */
  @SuppressWarnings("unchecked" /* because ResultHandler<?> is always ResultHandler<Object>*/)
  private void callResultHandler(ResultHandler<?> resultHandler, DefaultResultContext<Object> resultContext, Object rowValue) {
    /*

    1、
    （1）递增处理的行数据量(DefaultResultContext.resultCount)，该值用于检测处理的记录行数是否己经达到上下（在RowBounds。limit字段中记录了该上限）；
    （2）保存将当前行的结果对象(DefaultResultContext.resultObject)

     */
    resultContext.nextResultObject(rowValue);

    /* 2、存储当前行的结果对象 */
    // DefaultResultHandler
    ((ResultHandler<Object>) resultHandler).handleResult(resultContext);
  }

  /**
   * 判断是否可以接着往下处理结果：
   * 【没有停止往下进行处理 && 已经处理的结果数量，小于限制处理的结果数量】就可以继续往下进行处理
   *
   * @param context
   * @param rowBounds
   * @return
   */
  private boolean shouldProcessMoreRows(ResultContext<?> context, RowBounds rowBounds) {
    /**
     * 1、context.isStopped()：是否应该停止处理？
     * true：停止处理
     * false：不停止处理（恒定返回false）
     *
     * 2、context.getResultCount() < rowBounds.getLimit()：判断已经处理的结果数量，是否小于RowBounds.limit限制的结果数量
     * 只有"已经处理的结果数量，小于RowBounds.limit限制的结果数量"，才能继续处理；
     * 如果"已经处理的结果数量，大于RowBounds.limit限制的结果数量"，则不能继续往下进行处理
     */
    // 【没有停止往下进行处理 && 已经处理的结果数量，小于限制处理的结果数量】就可以继续往下进行处理
    return !context.isStopped() && context.getResultCount() < rowBounds.getLimit();
  }

  private void skipRows(ResultSet rs, RowBounds rowBounds) throws SQLException {
    if (rs.getType() != ResultSet.TYPE_FORWARD_ONLY/* 指示光标只能向前移动的 <code>ResultSet<code> 对象类型的常量。 */) {
      if (rowBounds.getOffset() != RowBounds.NO_ROW_OFFSET) {
        rs.absolute(rowBounds.getOffset());
      }
    } else {
      // 跳过offset行数据
      for (int i = 0; i < rowBounds.getOffset(); i++) {

        if (!rs.next()) {
          // 没有下一个数据就直接退出
          break;
        }

      }
    }
  }

  //
  // GET VALUE FROM ROW FOR SIMPLE RESULT MAP —— 从行中获取值以获得简单的结果图
  //

  /**
   * 获取单行数据结果值 —— 映射处理单行数据，返回映射好的结果对象：
   * (1)先创建结果对象
   * (2)字段映射，填充属性，有2种方式：
   * >>> (1)自动映射，填充属性
   * >>> (2)根据ResultMap中配置好的属性映射，填充属性
   *
   * @param rsw
   * @param resultMap
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object getRowValue(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix) throws SQLException {
    // 实例化ResultLoaderMap
    final ResultLoaderMap lazyLoader/* 延迟加载器 */ = new ResultLoaderMap();
    /*

    1、创建结果对象

    题外：💡里面会为懒加载的嵌套查询，创建代理对象！在代理对象的拦截器中，会保存ResultLoaderMap对象

    */
    // ⚠️创建结果对象（创建该行数据映射之后对应的结果对象，用于映射存储该行数据）
    // 题外：结果对象的类型，就是ResultMap上的type属性值
    // 注意：当前只是创建一个空的结果对象（映射对象、空属性的实例），例如：User，但是并没有赋值！
    Object rowValue = createResultObject(rsw, resultMap, lazyLoader, columnPrefix);

    /* 2、对结果对象进行字段映射 */
    // 如果获取到了结果对象，并且不存在结果对象类型所对应的TypeHandler，就开始进行字段映射
    // 题外：这一点确保了，例如我们返回int类型的数据，则会直接返回结果对象，不会进行映射！
    if (rowValue != null && !hasTypeHandlerForResultObject(rsw, resultMap.getType())/* 是否存在resultType(结果类型)对应的TypeHandler */) {
      /* 创建结果对象的MetaObject */
      // 创建结果对象的元数据对象，通过MetaObject，进行属性值反射设置
      final MetaObject metaObject/* 元数据 */ = configuration.newMetaObject(rowValue);

      // 成功映射任意属性，则foundValues为true，否则foundValues为false
      // 题外：自动映射默认不开启，官方也不推荐使用，忽略
      boolean foundValues = this.useConstructorMappings;/* 是否使用构造器的映射 */

      /* （1）自动映射，填充属性 —— 获取ResultMap中未配置，但是ResultSet中存在的列；通过列名去找结果对象中相同名称的属性名，进行自动映射；然后获取列值，填充属性 */
      // 判断是否使用"自动映射"匹配处理
      if (shouldApplyAutomaticMappings/* 是否使用自动映射 */(resultMap, false)) {
        // ⚠️自动映射ResultMap中未明确指定的列
        foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, columnPrefix) || foundValues;
      }

      /*

      （2）属性映射，填充属性 —— 根据ResultMap中配置好的属性映射，填充属性（获取ResultMap中配置了，且ResultSet中存在的列；然后获取属性值，填充属性）

       题外：💡里面有"懒加载的嵌套查询"的处理！
       >>> 在获取属性值的时候，如果是"嵌套查询"获取属性值：
       >>> 但是一级缓存中不存在嵌套查询的结果，并且是懒加载的嵌套查询，就会将"嵌套查询对应的ResultLoader(结果加载器)"，放入到ResultLoaderMap中

       */
      /**
       * 1、注意：⚠️如果里面有懒加载的嵌套查询，则不会去设置对应的属性值，也不会执行懒加载的嵌套查询
       * （1）但是在debug的情况下，applyPropertyMappings()方法内部虽然没有为结果对象设置懒加载嵌套查询所对应的属性值，也没有执行懒加载的嵌套查询，
       *  >>> 但是在执行完applyPropertyMappings()方法出来后，回到当前方法，我们发现结果对象中竟然有了"本属于懒加载嵌套查询"对应的属性值，
       *  >>> 之所以会产生这种情况，是因为触发了懒加载的执行，而之所以会触发了懒加载的执行，是因为默认触发懒加载的方法有toString()，
       *  >>> 在debug情况下，回到当前方法，由于当前方法中存在结果对象，它会默认执行结果对象的toString()，显示结果对象的数据给我们看，所以导致触发了懒加载的执行，所以结果对象中也有了对应的属性值；
       * （2）但是如果是非debug的情况下，则不会触发这种情况；
       * （3）或者是在debug的情况下，但是我们去除掉toString()作为触发懒加载执行的方法，则不会发生这种情况
       */
      // 映射ResultMap中明确指定需要映射的列
      foundValues = applyPropertyMappings/* 应用属性映射 */(rsw, resultMap, metaObject, lazyLoader, columnPrefix) || foundValues;

      foundValues = lazyLoader.size() > 0 || foundValues;

      /* （3）如果没有映射成功任何属性，则根据mybatis-config.xml中的returnInstanceForEmptyRow配置决定返回空的结果对象还是返回null */
      rowValue = foundValues || configuration.isReturnInstanceForEmptyRow() ? rowValue : null;
    }

    /* 3、返回结果对象 */
    return rowValue;
  }

  //
  // GET VALUE FROM ROW FOR NESTED RESULT MAP
  //

  private Object getRowValue(ResultSetWrapper rsw, ResultMap resultMap, CacheKey combinedKey, String columnPrefix, Object partialObject) throws SQLException {
    final String resultMapId = resultMap.getId();
    Object rowValue = partialObject;
    if (rowValue != null) {
      final MetaObject metaObject = configuration.newMetaObject(rowValue);
      putAncestor(rowValue, resultMapId);
      applyNestedResultMappings(rsw, resultMap, metaObject, columnPrefix, combinedKey, false);
      ancestorObjects.remove(resultMapId);
    } else {
      final ResultLoaderMap lazyLoader = new ResultLoaderMap();
      rowValue = createResultObject(rsw, resultMap, lazyLoader, columnPrefix);
      if (rowValue != null && !hasTypeHandlerForResultObject(rsw, resultMap.getType())) {
        final MetaObject metaObject = configuration.newMetaObject(rowValue);
        boolean foundValues = this.useConstructorMappings;
        if (shouldApplyAutomaticMappings(resultMap, true)) {
          foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, columnPrefix) || foundValues;
        }
        foundValues = applyPropertyMappings(rsw, resultMap, metaObject, lazyLoader, columnPrefix) || foundValues;
        putAncestor(rowValue, resultMapId);
        foundValues = applyNestedResultMappings(rsw, resultMap, metaObject, columnPrefix, combinedKey, true) || foundValues;
        ancestorObjects.remove(resultMapId);
        foundValues = lazyLoader.size() > 0 || foundValues;
        rowValue = foundValues || configuration.isReturnInstanceForEmptyRow() ? rowValue : null;
      }
      if (combinedKey != CacheKey.NULL_CACHE_KEY) {
        nestedResultObjects.put(combinedKey, rowValue);
      }
    }
    return rowValue;
  }

  private void putAncestor(Object resultObject, String resultMapId) {
    ancestorObjects.put(resultMapId, resultObject);
  }

  /**
   * 返回"是否自动映射"的标识
   *
   * @param resultMap
   * @param isNested      是否是嵌套的
   * @return
   */
  private boolean shouldApplyAutomaticMappings(ResultMap resultMap, boolean isNested) {
    /* 1、判断ResultMap中是否有设置autoMapping属性，如果有，则返回自动映射标识（autoMapping属性值） */
    // 判断ResultMap中是否有设置autoMapping属性，例如：例如：<resultMap autoMapping="true">
    if (resultMap.getAutoMapping() != null) {
      // 返回是否自动映射的标识
      return resultMap.getAutoMapping();
    }
    /* 2、ResultMap中没有设置autoMapping属性 */
    else {
      /* 2.1、是嵌套的 */
      // 检测是否为嵌套查询或是嵌套映射
      if (isNested) {
        // false
        return AutoMappingBehavior.FULL == configuration.getAutoMappingBehavior()/* 默认为AutoMappingBehavior.PARTIAL，代表只映射结果，不会映射嵌套的结果 */;
      }
      /* 2.2、不是嵌套的 */
      else {
        // true
        return AutoMappingBehavior.NONE != configuration.getAutoMappingBehavior();
      }
    }
  }

  //
  // PROPERTY MAPPINGS —— 属性映射
  //

  /**
   * 根据ResultMap中配置好的属性映射，填充属性（获取ResultMap中配置了，且ResultSet中存在的列；然后获取属性值，填充属性）
   */
  private boolean applyPropertyMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, ResultLoaderMap lazyLoader, String columnPrefix)
    throws SQLException {

    /*

    1、获取"映射的列名集合"，也就是：ResultMap中配置了映射，且在ResultSet中存在的列名

    注意：⚠️如果未获取到"映射的列名集合"，则会加载：1、映射的列名（ResultMap中配置了映射，且在ResultSet中存在的列名）；2、未映射的列名（和ResultMap中未配置映射，但在ResultSet中存在的列名）

     */
    final List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);

    boolean foundValues = false;

    /* 2、获取ResultMap中的ResultMapping集合 */
    // 获取ResultMap中配置的属性映射
    // 题外：<ResultMap>需要把那些属性映射，关联关系都获取到，然后进行挨个匹配
    final List<ResultMapping> propertyMappings/* 属性映射 */ = resultMap.getPropertyResultMappings();

    /* 3、遍历ResultMapping集合 */
    // 遍历属性映射，挨个字段映射
    for (ResultMapping propertyMapping/* 单行结果对象中某个属性的ResultMapping */ : propertyMappings) {

      // 配置的列名。为列名拼接上前缀，如果不存在前缀，就返回原始列名。
      String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);

      // 如果存在嵌套的ResultMap，则设置当前的列名为null
      if (propertyMapping.getNestedResultMapId() != null) {
        // the user added a column attribute to a nested result map, ignore it —— 用户将列属性添加到嵌套结果映射，忽略它
        // 该属性需要使用一个嵌套ResultMap进行映射，忽略column属性
        column = null;
      }

      /* 4、填充属性 */
      // 下面的逻辑主要处理三种场景
      // 场景1：复合列名，也就是：column是{prop1=col1,prop2=col2}这种形式的，一般与嵌套查询配合使用，表示将col1和col2的列值传递给内层嵌套
      // 场景2：基本类型的属性映射
      // 场景3：多结果集的场景，该属性值来自于另一个结果集
      if (
        // 是否存在复合列名
        propertyMapping.isCompositeResult() // 场景1
        // 映射的列名中，是否包含当前配置的列名
        || (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH))) // 场景2
        // 是否存在ResultSet（多结果集映射）
        || propertyMapping.getResultSet() != null // 场景3
      ) {

        /*

        4.1、获取属性值
        (1)嵌套查询：获取嵌套查询的结果，作为属性值

        题外：💡如果一级缓存中不存在嵌套查询的结果，并且是懒加载的，就会将嵌套查询对应的ResultLoader(结果加载器)，放入到ResultLoaderMap中

        (2)多数据结果集：返回一个"延迟"标记 —— DEFERRED，作为属性值，代表当前还没有属性值
        (3)正常情况：通过TypeHandler，获取当前行中，某个列名对应的数据，作为属性值

        */
        Object value = getPropertyMappingValue/* 获取属性映射的值 */(rsw.getResultSet(), metaObject, propertyMapping, lazyLoader, columnPrefix);

        /* 4.2、获取属性名 */
        // issue #541 make property optional —— 问题541，使属性可选
        final String property = propertyMapping.getProperty();

        // 没有配置属性名，则直接跳过。属性名都没有配置，我根本不清楚要设置到哪个属性上
        if (property == null) {
          continue;
        }
        // 属性值是"延迟"标记，则也跳过，因为这标记个代表的不是属性值，而是延迟加载属性值 —— 也就是说，要到后续触发延迟加载的条件，才会获取到属性值；
        // 属性值都没有获取到，我当然无法设置属性值了，所以跳过（题外：后续触发延迟加载条件的时候，是通过代理对象，加载属性值，以及设置属性值到结果对象中！）
        else if (value == DEFERRED/* 延迟 */) {
          // DEFERRED表示的是占位符对象
          foundValues = true;
          continue;
        }
        // 存在属性值，则代表我找到属性值了，所以设置找到属性值的标识为true，代表我找到属性值了
        if (value != null) {
          foundValues = true;
        }

        /* 4.3、设置属性值 */
        if (value != null || (configuration.isCallSettersOnNulls() && !metaObject.getSetterType(property).isPrimitive())) {
          // gcode issue #377, call setter on nulls (value is not 'found') —— gcode问题377，在null上调用setter（值不是“找到”）
          // 设置属性值
          // 使用反射工具类，给属性赋值
          metaObject.setValue(property, value);
        }
      }

    }

    return foundValues;
  }

  /**
   * 获取属性值
   * (1)嵌套查询：获取嵌套查询的结果，作为属性值
   * (2)多数据结果集：返回一个"延迟"标记，作为属性值，代表当前还没有属性值
   * (3)正常情况：通过TypeHandler，获取当前行中，某个列名对应的数据，作为属性值
   *
   * @param rs
   * @param metaResultObject                    当前单行结果对象对应的metaObject
   * @param propertyMapping                     单行结果对象中某个属性的ResultMapping
   * @param lazyLoader                          ResultLoaderMap
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object getPropertyMappingValue(ResultSet rs, MetaObject metaResultObject, ResultMapping propertyMapping, ResultLoaderMap lazyLoader, String columnPrefix)
    throws SQLException {

    /* 1、存在嵌套查询：获取嵌套查询的结果，作为属性值 */
    if (propertyMapping.getNestedQueryId() != null) {
      // 获取嵌套查询的结果
      return getNestedQueryMappingValue(rs, metaResultObject, propertyMapping, lazyLoader, columnPrefix);
    }
    /* 2、存在resultSet属性(多数据结果集)：返回一个"延迟"标记，作为属性值，代表当前还没有属性值 */
    // 存在resultSet属性，代表引入另外一个ResultSet作为该属性值，而不是当前ResultSet中的数据做该属性值
    // 题外：只有<collection>、<association>标签中存在resultSet属性
    else if (propertyMapping.getResultSet() != null) {
      addPendingChildRelation/* 添加待处理的子关系 */(rs, metaResultObject, propertyMapping);   // TODO is that OK? —— 那样行吗？
      return DEFERRED;
    }
    /* 3、其余情况：通过TypeHandler，获取当前行中，某个列名对应的数据，作为属性值 */
    else {
      // TypeHandler
      final TypeHandler<?> typeHandler = propertyMapping.getTypeHandler();
      // 配置的列名
      final String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
      // 通过TypeHandler，获取ResultSet中当前行里面，列名对应的数据，作为属性值
      return typeHandler.getResult(rs, column);
    }

  }

  /**
   * 创建自动映射集合：
   *
   * 1、获取"未映射的列名集合"，也就是：ResultMap中未配置映射，但ResultSet中存在的列名。
   * 注意：⚠️如果未获取到"未映射的列名集合"，则会加载：1、映射的列名集合（ResultMap中配置了映射，且在ResultSet中存在的列名）；2、和未映射的列名集合（ResultMap中未配置映射，但在ResultSet中存在的列名）
   *
   * 2、然后对未映射的列，进行自动映射，构建"未映射的列"对应的UnMappedColumnAutoMapping对象
   * 🚩如何对未映射的列，进行自动映射？
   * （1）去结果对象中，查找列名对应的属性名；
   * （2）然后继续去结果对象中，查找属性名对应的set方法的参数类型，作为属性类型；
   * （3）然后通过"属性类型"和"列名的jdbc类型"，去获取对应的TypeHandler；
   * （4）如果最终可以获取到对应的TypeHandler，就代表自动映射成功了，就会构建未映射的列，所对应的UnMappedColumnAutoMapping对象；
   *  UnMappedColumnAutoMapping里面存放了，列名，属性名，TypeHandler
   *
   * @param rsw
   * @param resultMap
   * @param metaObject            结果对象的metaObject
   * @param columnPrefix
   */
  private List<UnMappedColumnAutoMapping> createAutomaticMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String columnPrefix) throws SQLException {
    // 映射key
    // com.msb.mybatis_02.dao.UserDao.getUserByUser2_userMap:null
    final String mapKey = resultMap.getId() + ":" + columnPrefix;

    /* 1、先从autoMappingsCache(自动映射缓存)缓存中获取自动映射集合，缓存中存在就直接返回了 */
    // 题外：⚠️未映射的列，所采取的映射方式就是自动映射，所以叫UnMappedColumnAutoMapping(未映射的列,自动映射)
    List<UnMappedColumnAutoMapping/* 未映射的列,自动映射 */> autoMapping = autoMappingsCache.get(mapKey);

    /* 2、缓存中不存在 */
    if (autoMapping == null) {
      autoMapping = new ArrayList<>();

      /*

      2.1、获取"未映射的列名集合"，也就是：ResultMap中未配置映射，但ResultSet中存在的列名。

      注意：⚠️如果未获取到"未映射的列名集合"，则会加载：1、映射的列名集合（ResultMap中配置了映射，且在ResultSet中存在的列名）；2、和未映射的列名集合（ResultMap中未配置映射，但在ResultSet中存在的列名）

      */
      final List<String> unmappedColumnNames = rsw.getUnmappedColumnNames/* 获取未映射的列名 */(resultMap, columnPrefix);

      // Remove the entry to release the memory —— 删除条目以释放内存
      // 删除当前mapKey的构造器映射
      List<String> mappedInConstructorAutoMapping = constructorAutoMappingColumns.remove(mapKey);

      // "未映射的列名集合"中移除"构造器映射列名"，得到纯粹的"未映射的列名"
      if (mappedInConstructorAutoMapping != null) {
        unmappedColumnNames.removeAll(mappedInConstructorAutoMapping);
      }

      /* 2.2、遍历"未映射的列名集合"，进行自动映射，构建"未映射的列"对应的UnMappedColumnAutoMapping对象，放入autoMapping中 */
      for (String columnName : unmappedColumnNames) {
        String propertyName = columnName;

        /* 剔除列名前缀；以及忽略掉不带指定前缀的列名(因为如果列名不包含指定前缀，则证明当前列名是不符合期待的) */
        // 如果存在列名前缀
        if (columnPrefix != null && !columnPrefix.isEmpty()) {
          // When columnPrefix is specified,
          // ignore columns without the prefix.
          // 上面翻译：指定columnPrefix时，忽略不带前缀的列

          // 如果当前列名，包含列名前缀，则去除列名前缀
          if (columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
            propertyName = columnName.substring(columnPrefix.length());
          }
          // 如果当前列名，不包含列名前缀，则忽略当前列名。因为不包含列名前缀，则证明当前列名是不符合期待的。
          else {
            continue;
          }
        }

        /**

         🚩如何对未映射的列，进行自动映射？
         （1）去结果对象中，查找列名对应的属性名；
         （2）然后继续去结果对象中，查找属性名对应的set方法的参数类型，作为属性类型；
         （3）然后通过"属性类型"和"列名的jdbc类型"，去获取对应的TypeHandler；
         （4）如果最终可以获取到对应的TypeHandler，就代表自动映射成功了，就会构建未映射的列，所对应的UnMappedColumnAutoMapping对象
         UnMappedColumnAutoMapping里面存放了，列名，属性名，TypeHandler

         */

        /* 去结果对象中，查找列名对应的属性名 */
        // 去结果对象中，查找列名对应的属性名
        // 题外：有可能列名是user_id，但是在结果对象中的属性名是userId，这个时候mapUnderscoreToCamelCase，就发挥了作用，
        // >>> 如果支持下划线到驼峰的映射，则通过user_id，就可以找到userId属性；如果不支持，则找不到
        final String property = metaObject.findProperty(propertyName, configuration.isMapUnderscoreToCamelCase()/* 下划线到驼峰 */);

        /* 存在属性名；且结果对象中，有属性名对应的set方法 */
        if (property != null && metaObject.hasSetter(property)) {

          /* 看下ResultMap中配置的属性名中，是否有当前列名对应的属性名。有的话，就证明当前列名不是未映射的，则跳过当前列名 */
          if (resultMap.getMappedProperties()/* ResultMap中配置的属性名 */.contains(property)) {
            continue;
          }

          // 获取属性名对应的set方法的参数类型，也就相当于获取属性类型
          final Class<?> propertyType = metaObject.getSetterType(property);

          /* 存在属性类型对应的TypeHandler，则构建当前列名对应的UnMappedColumnAutoMapping，添加到autoMapping中 */
          // 存在属性类型与"列名jdbc类型"对应的TypeHandler
          if (typeHandlerRegistry.hasTypeHandler(propertyType, rsw.getJdbcType(columnName)/* 获取列名对应的jdbc类型 */)) {
            // 获取属性类型与列名对应的TypeHandler
            // 题外：rsw.getTypeHandler()里面可以通过列名，获取对应的jdbc类型，所以传入的是列名，实际用的还是jdbc类型
            final TypeHandler<?> typeHandler = rsw.getTypeHandler(propertyType, columnName);
            // 构建当前列名的UnMappedColumnAutoMapping
            autoMapping.add(new UnMappedColumnAutoMapping/* 未映射的列自动映射 */(columnName, property, typeHandler, propertyType.isPrimitive()));
          }
          // 不存在属性类型对应的TypeHandler，则什么都不做
          else {
            // 什么事都没做
            configuration.getAutoMappingUnknownColumnBehavior()
              .doAction(mappedStatement, columnName, property, propertyType);
          }
        }
        // 不存在属性名；或者存在属性名，但是结果对象中，没有属性名对应的set方法
        else {
          // 什么事都没做
          configuration.getAutoMappingUnknownColumnBehavior()
            .doAction(mappedStatement, columnName, (property != null) ? property : propertyName, null);
        }
      }

      /*

      3、⚠️往autoMappingsCache中放置mapKey和"对应的自动映射集合"

      题外：未映射的列，所采取的映射方式就是自动映射

      */
      autoMappingsCache.put(mapKey, autoMapping);
    }

    return autoMapping;
  }

  /**
   * 自动映射，填充属性（获取ResultMap中未配置，但是ResultSet中存在的列；通过列名去找结果对象中相同名称的属性名，进行自动映射；然后获取列值，填充属性）
   *
   *
   * @param rsw
   * @param resultMap
   * @param metaObject          结果对象的metaObject
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private boolean applyAutomaticMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String columnPrefix) throws SQLException {
    /*

    一、获取自动映射集合

    1、获取"未映射的列名集合"，也就是：ResultMap中未配置映射，但ResultSet中存在的列名。
    注意：⚠️如果未获取到"未映射的列名集合"，则会加载：1、映射的列名（ResultMap中配置了映射，且在ResultSet中存在的列名）；2、和未映射的列名（ResultMap中未配置映射，但在ResultSet中存在的列名）

    2、然后对未映射的列，进行自动映射，构建"未映射的列"对应的UnMappedColumnAutoMapping对象

    🚩如何对未映射的列，进行自动映射？
    （1）去结果对象中，查找列名对应的属性名；
    （2）然后继续去结果对象中，查找属性名对应的set方法的参数类型，作为属性类型；
    （3）然后通过"属性类型"和"列名的jdbc类型"，去获取对应的TypeHandler；
    （4）如果最终可以获取到对应的TypeHandler，就代表自动映射成功了，就会构建未映射的列，所对应的UnMappedColumnAutoMapping对象；
     UnMappedColumnAutoMapping里面存放了，列名，属性名，TypeHandler

     简单概括：就是通过列名去找结果对象中相同名称的属性名，进行自动映射

     */
    /**
     * UnMappedColumnAutoMapping类代表的意思：
     * 查询到具体结果之后，实际对象也有了，接下来要进行字段映射。但是存在一些，ResultMap中未配置映射，但ResultSet中存在的列，
     * 这些列与类中的属性，无法通过ResultMap进行正常匹配映射，所以就要采取自动映射了，我们把自动映射好的关系，存放在了UnMappedColumnAutoMapping里面；
     * UnMappedColumnAutoMapping里面收录了列名、以及列名在结果对象中对应的属性名，以及"结果对象中的属性类型"与"列名jdbc类型"所对应的TypeHandler，
     * 后续就可以直接获取列数据，设置到结果对象对应的属性里面去了
     */
    // ⚠️获取自动映射集合
    // 获取ResultMap中未配置映射，但ResultSet中存在的列；然后对这些未配置映射的列，进行自动映射；构建这些列所对应的UnMappedColumnAutoMapping集合
    // 题外：如果ResultMap中设置的resultType为HashMap的话，则全部的列都会在这
    List<UnMappedColumnAutoMapping/* 未映射的列，自动映射 */> autoMapping = createAutomaticMappings(rsw, resultMap, metaObject, columnPrefix);

    // 是否找到属性值的标识，false代表没有找到
    boolean foundValues = false;

    /* 二、填充属性 */
    if (!autoMapping.isEmpty()) {
      // 遍历自动映射集合
      for (UnMappedColumnAutoMapping mapping : autoMapping) {

        /* 1、通过"属性"和"列名"所对应的TypeHandler，从ResultSet中获取自动映射的列的值，作为属性值 */
        final Object value = mapping.typeHandler.getResult(rsw.getResultSet(), mapping.column/* 自动映射的列名 */);

        if (value != null) {
          // 找到属性值了
          foundValues = true;
        }

        /* 2、设置属性值到结果对象中 */
        if (value != null || (configuration.isCallSettersOnNulls() && !mapping.primitive)) {
          // gcode issue #377, call setter on nulls (value is not 'found') —— gcode问题377，在null上调用setter（值不是“找到”）
          metaObject.setValue(mapping.property/* 属性名 */, value/* 属性值 */);
        }
      }
    }

    return foundValues;
  }

  // MULTIPLE RESULT SETS —— 多个结果集

  /**
   * 当前ResultSet行结果，与原先的ResultSet行结果进行链接
   *
   * @param rs                    单行结果对象的metaObject
   * @param parentMapping         "resultSet属性""所在标签的ResultMapping"
   * @param rowValue              当前值 —— resultSet属性值所引入的ResultSet的行结果对象
   */
  private void linkToParents(ResultSet rs, ResultMapping parentMapping, Object rowValue) throws SQLException {
    /* 1、为多个结果创建CacheKey */
    /**
     * 1、注意：⚠️foreignColumn，这是将"当前ResultSet行结果，与原先的ResultSet行结果进行链接"的关键！表示，我当前表字段，所关联的表的字段（注意，是关联的表的字段）
     * 例如：一个用户表，用户有一个账户表，账户与用户的关系一对一。
     * >>> 从账户表角度来看，在账户表当中有一个uid，代表了所属的用户，关联的是用户表的id；
     * >>> 所以从账户表的角度而言，账户表的uid字段，它的️foreignColumn，就是用户表的id字段，参考如下：
     *
     *   <resultMap id="accountMap" type="com.msb.other.resultSets.t_02.entity.Account">
     *     <id column="id" property="id"/>
     *     <result column="uid" property="uid"/>
     *     <result column="money" property="money"/>
     *     <association property="user" javaType="com.msb.other.resultSets.t_02.entity.User"
     *                  resultSet="testResultSet" foreignColumn="id" column="uid">
     *     </association>
     *   </resultMap>
     *
     *   <select id="findAll" resultMap="accountMap" resultSets="testResultSet">
     *     select *
     *     from hm_account
     *   </select>
     */
    CacheKey parentKey = createKeyForMultipleResults(rs, parentMapping, parentMapping.getColumn(), parentMapping.getForeignColumn());

    /* 2、获取CacheKey对应的"PendingRelation集合" */
    /**
     * 注意：⚠️这是一个集合，里面有一个个的单行结果对象对应的metaObject，这样我们就能把"当前行的结果对象"，作为属性值，设置到原先的"单行结果对象"中，
     * 例如：就是把testResultSet中当前行的结果对象，作为属性值，设置到最外层的resultMap对应的ResultSet的单行结果对象当中，
     * 也就是两个ResultSet，行与行之间的数据一一对应，其中testResultSet ResultSet的行结果对象作为另一个ResultSet行结果对象的属性值！
     *
     *   <resultMap id="accountMap" type="com.msb.other.resultSets.t_02.entity.Account">
     *     <id column="id" property="id"/>
     *     <result column="uid" property="uid"/>
     *     <result column="money" property="money"/>
     *     <association property="user" javaType="com.msb.other.resultSets.t_02.entity.User"
     *                  resultSet="testResultSet" foreignColumn="id" column="uid">
     *     </association>
     *   </resultMap>
     *
     *   <select id="findAll" resultMap="accountMap" resultSets="testResultSet">
     *     select *
     *     from hm_account
     *   </select>
     */
    List<PendingRelation> parents = pendingRelations.get(parentKey);

    /* 3、遍历"PendingRelation集合"，把当前ResultSet行结果，与原先的ResultSet行结果进行链接 */
    if (parents != null) {
      for (PendingRelation parent : parents) {
        // 存在PendingRelation && 存在属性值
        if (parent != null && rowValue != null) {
          // 当前ResultSet行结果，与原先的ResultSet行结果进行链接
          linkObjects(parent.metaObject, parent.propertyMapping, rowValue);
        }
      }
    }

  }

  /**
   * 由于存在resultSet属性(多数据结果集)，所以构建待处理的子关系
   *
   * 题外：存在resultSet属性，代表引入另外一个ResultSet作为该属性值，而不是当前ResultSet中的数据做该属性值
   * 题外：只有<collection>、<association>标签中存在resultSet属性
   *
   * @param rs
   * @param metaResultObject                  当前单行结果对象对应的metaObject
   * @param parentMapping                     单行结果对象中某个属性的ResultMapping
   * @throws SQLException
   */
  private void addPendingChildRelation/* 添加待处理的子关系 */(ResultSet rs, MetaObject metaResultObject, ResultMapping parentMapping) throws SQLException {
    /* 1、构建pendingRelations集合 */

    /* （1）为多个结果创建CacheKey */
    CacheKey cacheKey = createKeyForMultipleResults/* 为多个结果创建key */(rs, parentMapping, parentMapping.getColumn(), parentMapping.getColumn());

    /* （2）创建PendingRelation */
    // 创建PendingRelation
    PendingRelation/* 待处理的关系 */ deferLoad/* 延迟加载 */ = new PendingRelation();
    // 当前单行结果对象对应的metaObject
    deferLoad.metaObject = metaResultObject;
    // 单行结果对象中某个属性的ResultMapping（也就是，当前"resultSet属性""所在标签的ResultMapping"）
    deferLoad.propertyMapping = parentMapping;

    /* （3）将cacheKey和PendingRelation集合的映射关系放入到pendingRelations中 */
    List<PendingRelation> relations = MapUtil.computeIfAbsent(pendingRelations, cacheKey, k -> new ArrayList<>());
    // issue #255
    // 将PendingRelation放入到pendingRelations集合中
    relations.add(deferLoad);

    /* 2、往nextResultMaps中存放"resultSet属性"和"resultSet属性所在标签的ResultMapping"的对应关系 */
    // 从nextResultMaps中，获取"resultSet属性""所在标签的ResultMapping"
    ResultMapping previous = nextResultMaps.get(parentMapping.getResultSet()/* resultSet属性 */);
    if (previous == null) {
      nextResultMaps.put(parentMapping.getResultSet()/* resultSet属性 */, parentMapping/* resultSet属性所在标签的ResultMapping */);
    } else {
      // 如果存在，但是存在的，与当前的不相同，就报错
      if (!previous.equals(parentMapping)) {
        throw new ExecutorException("Two different properties are mapped to the same resultSet"/* 两个不同的属性映射到同一个结果集 */);
      }
    }

  }

  private CacheKey createKeyForMultipleResults(ResultSet rs, ResultMapping resultMapping, String names, String columns) throws SQLException {
    CacheKey cacheKey = new CacheKey();
    cacheKey.update(resultMapping); // 往updateList中添加resultMapping

    if (columns != null && names != null) {

      String[] columnsArray = columns.split(",");
      String[] namesArray = names.split(",");

      for (int i = 0; i < columnsArray.length; i++) {
        Object value = rs.getString(columnsArray[i]);
        if (value != null) {
          cacheKey.update(namesArray[i]);
          cacheKey.update(value);
        }
      }
    }
    return cacheKey;
  }

  //
  // INSTANTIATION & CONSTRUCTOR MAPPING —— 实例化和构造函数映射
  //

  /**
   * 创建结果对象（结果对象类型为：resultMap中的type类型），一共4种方式：
   * （1）通过TypeHandler，提取ResultSet当中当前行数据，构建结果对象
   * 题外：这也就是为什么，我们的返回值类型是int的时候，不需要配置映射，mybatis会自动帮我们返回值的原因！
   * （2）通过配置的构造器，实例化结果对象
   * （3）使用默认的无参构造器，实例化结果对象
   * （4）通过构造器自动映射的方式创建结果对象：
   * >>> 先查询到合适的构造器，用于后续的自动映射；然后通过构造器自动映射的方式，得出构造器参数值，然后创建结果对象(resultType类型)
   */
  private Object createResultObject(ResultSetWrapper rsw, ResultMap resultMap, ResultLoaderMap lazyLoader, String columnPrefix) throws SQLException {
    // "是否使用构造函数创建结果对象"的标识
    this.useConstructorMappings = false; // reset previous mapping result —— 重置之前的映射结果

    /* 1、记录构造器的参数类型 */
    final List<Class<?>> constructorArgTypes = new ArrayList<>();

    /* 2、记录构造器的参数值 */
    final List<Object> constructorArgs = new ArrayList<>();

    /* 3、⚠️创建结果对象 */
    // 创建一个空壳的，该行数据映射之后对应的结果对象
    // 注意：当前只是创建一个空的结果对象（映射对象、空属性的实例），例如：User，但是并没有赋值！
    Object resultObject = createResultObject/* 创建结果对象 */(rsw, resultMap, constructorArgTypes, constructorArgs, columnPrefix);

    /* 4、为懒加载的嵌套查询，创建代理对象 */
    // 结果对象不为null，并且不存在resultType(结果类型)对应的TypeHandler
    if (resultObject != null && !hasTypeHandlerForResultObject(rsw, resultMap.getType())) {
      final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
      for (ResultMapping propertyMapping : propertyMappings) {
        // issue gcode #109 && issue #149 —— 问题gcode 109&&问题149

        // 如果包含嵌套查询，且配置了是延迟加载的，则创建代理对象
        if (propertyMapping.getNestedQueryId() != null/* 存在嵌套查询 */ && propertyMapping.isLazy()/* 是否是懒加载 */) {
          // 创建"结果对象"的"动态代理对象"
          resultObject = configuration.getProxyFactory()/* JavassistProxyFactory */.createProxy(resultObject, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
          break;
        }

      }
    }

    // 结果对象不为null，并且构造器参数类型不为空，则代表是使用构造器创建的结果对象
    this.useConstructorMappings = resultObject != null && !constructorArgTypes.isEmpty(); // set current mapping result —— 设置当前映射结果

    return resultObject;
  }

  /**
   * 创建结果对象（结果对象类型为：resultMap中的type类型），一共4种方式：
   * （1）通过TypeHandler，提取ResultSet当中当前行数据，构建结果对象
   * 题外：这也就是为什么，我们的返回值类型是int的时候，不需要配置映射，mybatis会自动帮我们返回值的原因！
   * （2）通过配置的构造器，实例化结果对象
   * （3）使用默认的无参构造器，实例化结果对象
   * （4）通过构造器自动映射的方式创建结果对象：
   * >>> 先查询到合适的构造器，用于后续的自动映射；然后通过构造器自动映射的方式，得出构造器参数值，然后创建结果对象(resultType类型)
   *
   * @param rsw
   * @param resultMap
   * @param constructorArgTypes
   * @param constructorArgs
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object createResultObject(ResultSetWrapper rsw, ResultMap resultMap, List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix)
    throws SQLException {

    /* 1、获取结果对象类型 —— resultType */
    // 获取映射的结果对象类型
    // 题外：也就是获取ResultMap中的type属性，作为映射的结果类型，
    // >>> 例如：<resultMap id="userMap" type="com.msb.other.discriminator.pojo.User">中的User
    final Class<?> resultType = resultMap.getType();

    /* 2、创建"结果对象类型"对应的MetaClass对象 */
    final MetaClass metaType = MetaClass.forClass(resultType, reflectorFactory);
    /**
     * 例如：
     *   <resultMap id="HealthReportMaleResultMap" type="com.msb.other.discriminator.pojo.User">
     *     <constructor>
     *       <arg column="id" javaType="int"/>
     *       <arg column="username" javaType="string" resultMap="" select="" jdbcType="" typeHandler="" columnPrefix="" name=""/>
     *     </constructor>
     *   </resultMap>
     */
    // 获取ResultMap中<constructor>标签信息
    // 题外：如果该集合不为空，则可以通过该集合确定结果类型中的唯一构造函数
    final List<ResultMapping> constructorMappings = resultMap.getConstructorResultMappings()/* 获取构造函数结果映射 */;

    /* 3、创建结果对象 */

    /*

    3.1、如果存在resultType对应的TypeHandler，则通过TypeHandler，提取ResultSet当中当前行数据，构建结果对象(resultType类型)

    题外：这也就是为什么，我们的返回值类型是int的时候，不需要配置映射，mybatis会自动帮我们返回值的原因！

    */
    // 判断在typeHandlerRegistry.typeHandlerMap中，是否存在resultType对应的TypeHandler；
    // 有的话，则使用resultType对应的TypeHandler，转换ResultSet中当前行里面的数据，成为resultType类型的值，作为结果对象，进行返回
    if (hasTypeHandlerForResultObject(rsw, resultType)) {
      return createPrimitiveResultObject/* 创建原始结果对象 */(rsw, resultMap, columnPrefix);
    }
    /* 3.2、通过配置的构造器，实例化结果对象(resultType类型) */
    // 存在<constructor>标签配置，则：
    // (1)获取配置的构造器参数类型；以及根据配置的列名，从ResultSet中，获取构造器类型对应的参数值
    // (2)然后根据构造器参数类型和参数值，实例化结果对象
    // 题外：在实例化对象的时候，使用的是objectFactory
    else if (!constructorMappings.isEmpty()) {
      return createParameterizedResultObject/* 创建参数化结果对象 */(rsw, resultType, constructorMappings, constructorArgTypes, constructorArgs, columnPrefix);
    }
    /* 3.3、使用默认的无参构造器，实例化结果对象(resultType类型) */
    // 是接口 || 存在默认的构造器
    else if (resultType.isInterface()/* 是接口 */ || metaType.hasDefaultConstructor()/* 存在默认构造器 */) {
      // 使用默认的无参构造器，创建结果对象
      // 注意：⚠️此时只是创建了一个空对象，并未填充属性
      // 题外：在实例化对象的时候，使用的是objectFactory
      // DefaultObjectFactory
      return objectFactory.create(resultType);
    }
    /* 3.4、通过构造器自动映射的方式创建结果对象：先查询到合适的构造器，用于后续的自动映射；然后通过构造器自动映射的方式，得出构造器参数值，然后创建结果对象(resultType类型) */
    else if (shouldApplyAutomaticMappings/* 是否使用自动映射 */(resultMap, false)) {
      /**
       * 1、先查找合适的构造器，用于后续的自动映射
       *
       * （1）如果只有一个构造器，就返回这个构造器
       * （2）获取标注了@AutomapConstructor的构造器
       * （3）【构造器参数个数，和ResultSet中的列数相等；并且存在每个"构造器参数类型"和"该参数类型索引位置所对应的列的jdbc类型"的TypeHandler】的构造器
       *
       * 2、然后通过构造器自动映射的方式，得出构造器参数值，然后创建结果对象。
       *
       * 构造器自动映射的方式，得出构造器参数值，一共有2种：
       * （1）根据"构造器参数顺序"和"ResultSet中列的顺序"一一对应，得出构造器参数对应的参数值
       * （2）根据"构造器参数名"和"ResultSet中的列名"相匹配，得出构造器参数对应的参数值
       *
       * 题外：构造器自动映射方式，就是如何获取构造器参数类型和参数值的方式
       */
      return createByConstructorSignature/* 通过构造函数签名创建 */(rsw, resultMap, columnPrefix, resultType, constructorArgTypes, constructorArgs);
    }

    /* 4、创建结果对象失败，抛出异常 */

    // 初始化失败，抛出异常
    throw new ExecutorException("Do not know how to create an instance of " + resultType);
  }

  /**
   * 通过配置的构造器参数，也就是使用对应的有参构造器，实例化结果对象
   * (1)获取配置的构造器参数类型；以及根据配置的列名，从ResultSet中，获取构造器类型对应的参数值
   * (2)然后根据构造器参数类型和参数值，实例化对象
   *
   * @param rsw                             ResultSetWrapper
   * @param resultType                      结果映射类型，resultMap中的type属性
   * @param constructorMappings             构造器映射，也就是<constructor>标签内容
   * @param constructorArgTypes             构造器参数类型
   * @param constructorArgs                 构造器参数值
   * @param columnPrefix                    列名前缀
   * @return
   */
  Object createParameterizedResultObject(ResultSetWrapper rsw, Class<?> resultType, List<ResultMapping> constructorMappings,
                                         List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix) {
    // 是否找到参数值的标识
    boolean foundValues = false;

    /* 一、遍历配置的每个构造器参数映射。根据"配置的构造器参数映射"和"ResultSet中的结果"，构建对应的构造器参数类型和参数值。 */
    /**
     * 参考：
     * <resultMap id="HealthReportMaleResultMap" type="com.msb.other.discriminator.pojo.User" autoMapping="true">
     *   <constructor>
     *     <arg column="id" javaType="int"/>
     *     <arg column="username" javaType="string" resultMap="" select="" jdbcType="" typeHandler="" columnPrefix="" name=""/>
     *   </constructor>
     * </resultMap>
     *
     * 题外：<constructor>只能定义<arg>和<idArg>这2个子标签
     */
    // 遍历配置的每个构造器参数映射
    for (ResultMapping constructorMapping : constructorMappings) {

      /* 1、获取构造器参数类型 */
      // 获取构造器参数类型
      // 例如：<arg column="id" javaType="int"/>中的javaType
      final Class<?> parameterType = constructorMapping.getJavaType();

      /* 2、获取构造器参数值：根据列名，从ResultSet中，获取构造器类型对应的参数值 */

      /* 2.1、列名 */
      // 获取构造器参数对应的列名
      // 例如：<arg column="id" javaType="int"/>中的column
      final String column = constructorMapping.getColumn();

      /* 2.2、根据列名，从ResultSet中，获取构造器类型对应的参数值 */
      // 构造器参数值
      final Object value;
      try {
        /* （1）存在嵌套查询，则发起嵌套查询，得到查询的结果，作为构造器参数值 */
        // 参考：<arg column="username" javaType="string" select=""/>
        if (constructorMapping.getNestedQueryId()/* 获取嵌套查询ID */ != null) {
          // 发起嵌套查询，得到嵌套查询的结果
          value = getNestedQueryConstructorValue/* 获取嵌套查询构造函数值 */(rsw.getResultSet(), constructorMapping, columnPrefix);
        }
        /* （2）存在嵌套的ResultMap，通过嵌套的ResultMap，和ResultSet中的数据，构建对应的结果映射，作为构造器参数值 */
        // 参考：<arg column="username" javaType="string" resultMap=""/>
        else if (constructorMapping.getNestedResultMapId() != null) {
          // 获取嵌套的ResultMap
          // 题外：⚠️因为构造器参数有可能是一个复杂的引用对象，例如：User
          final ResultMap resultMap = configuration.getResultMap(constructorMapping.getNestedResultMapId());
          // 通过ResultMap，和ResultSet中的数据，构建对应的结果映射，作为构造器参数值
          value = getRowValue(rsw, resultMap, getColumnPrefix(columnPrefix, constructorMapping));
        }
        /* （3）其它情况，则直接用配置的"构造器参数名"作为"列名"，去ResultSet中获取"列值"，作为构造器参数值 */
        else {
          final TypeHandler<?> typeHandler = constructorMapping.getTypeHandler();
          value = typeHandler.getResult(rsw.getResultSet(), prependPrefix(column, columnPrefix));
        }
      } catch (ResultMapException | SQLException e) {
        throw new ExecutorException("Could not process result for mapping: " + constructorMapping, e);
      }

      /* 3、添加构造器参数类型 */
      constructorArgTypes.add(parameterType);
      /* 4、添加构造器参数值 */
      constructorArgs.add(value);

      foundValues = value != null || foundValues;
    }

    /* 二、根据构造器参数类型和参数值，实例化对象 */
    // DefaultObjectFactory
    return foundValues ? objectFactory.create(resultType/* 结果类型 */, constructorArgTypes/* 构造器参数类型 */, constructorArgs/* 构造器参数值 */) : null;
  }


  /**
   * 1、先查找合适的构造器，用于后续的自动映射
   *
   * （1）如果只有一个构造器，就返回这个构造器
   * （2）获取标注了@AutomapConstructor的构造器
   * （3）【构造器参数个数，和ResultSet中的列数相等；并且存在每个"构造器参数类型"和"该参数类型索引位置所对应的列的jdbc类型"的TypeHandler】的构造器
   *
   * 2、然后通过构造器自动映射的方式，得出构造器参数值，然后创建结果对象。
   *
   * 构造器自动映射的方式，得出构造器参数值，一共有2种：
   * （1）根据"构造器参数顺序"和"ResultSet中列的顺序"一一对应，得出构造器参数对应的参数值
   * （2）根据"构造器参数名"和"ResultSet中的列名"相匹配，得出构造器参数对应的参数值
   *
   * 题外：构造器自动映射方式，就是如何获取构造器参数类型和参数值的方式
   */
  private Object createByConstructorSignature(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix, Class<?> resultType,
                                              List<Class<?>> constructorArgTypes, List<Object> constructorArgs) throws SQLException {
    /* 2、通过构造器自动映射的方式，创建结果对象 */
    return applyConstructorAutomapping(rsw, resultMap, columnPrefix, resultType, constructorArgTypes, constructorArgs,
      /* 1、查找合适的构造器，用于后续的自动映射 */
      findConstructorForAutomapping/* 查找自动映射的构造函数 */(resultType, rsw)
        // 没有找到合适的构造器，就报错
        .orElseThrow(() -> new ExecutorException("No constructor found in " + resultType.getName() + " matching " + rsw.getClassNames()))
    );
  }


  /**
   * 查找合适的构造器，用于后续的自动映射
   * （1）如果只有一个构造器，就返回这个构造器
   * （2）获取标注了@AutomapConstructor的构造器
   * （3）【构造器参数个数，和ResultSet中的列数相等；并且存在每个"构造器参数类型"和"该参数类型索引位置所对应的列的jdbc类型"的TypeHandler】的构造器
   *
   * @param resultType
   * @param rsw
   * @return
   */
  private Optional<Constructor<?>> findConstructorForAutomapping(final Class<?> resultType, ResultSetWrapper rsw) {
    // 获取所有的构造器
    Constructor<?>[] constructors = resultType.getDeclaredConstructors();

    /* 1、如果只有一个构造器，就返回这个构造器 */
    if (constructors.length == 1) {
      return Optional.of(constructors[0]);
    }

    /*

    2、获取标注了@AutomapConstructor的构造器。

    如果有多个构造器标注了@AutomapConstructor，则报错；只允许一个构造器标注@AutomapConstructor

    */
    Optional<Constructor<?>> annotated = Arrays.stream(constructors)
      .filter(x -> x.isAnnotationPresent(AutomapConstructor/* 自动映射构造函数 */.class))
      .reduce((x, y) -> {
        throw new ExecutorException("@AutomapConstructor should be used in only one constructor."/* @AutomapConstructor只能在一个构造函数中使用。 */);
      });
    if (annotated.isPresent()) {
      return annotated;
    }
    /* 3、如果是基于参数名称的构造函数自动映射，则报错 */
    else if (configuration.isArgNameBasedConstructorAutoMapping()/* 是基于参数名称的构造函数自动映射 */) {
      // Finding-best-match type implementation is possible,
      // but using @AutomapConstructor seems sufficient.
      throw new ExecutorException(MessageFormat.format(
        "'argNameBasedConstructorAutoMapping' is enabled and the class ''{0}'' has multiple constructors, so @AutomapConstructor must be added to one of the constructors.",
        resultType.getName()));
    }
    /*

    4、查找【构造器参数个数，和ResultSet中的列数相等；并且存在每个"构造器参数类型"和"该参数类型索引位置所对应的列的jdbc类型"的TypeHandler】的构造器

     */
    else {
      return Arrays.stream(constructors).filter(x -> findUsableConstructorByArgTypes/* 按参数类型，查找可用的构造函数 */(x, rsw.getJdbcTypes()/* ResultSet中所有列的jdbc类型 */)).findAny();
    }
  }

  /**
   * 只有当前【构造器参数个数，和ResultSet中的列数相等；并且存在每个"构造器参数类型"和"该参数类型索引位置所对应的列的jdbc类型"的TypeHandler】的构造器，才会返回true，代表该构造器合适
   *
   * @param constructor     构造器
   * @param jdbcTypes       ResultSet中所有列的jdbc类型
   */
  private boolean findUsableConstructorByArgTypes(final Constructor<?> constructor, final List<JdbcType> jdbcTypes) {
    // 构造器参数类型
    final Class<?>[] parameterTypes = constructor.getParameterTypes();

    /* 1、构造器的参数个数，不等于ResultSet中的列数，则返回false，代表该构造器不适用 */
    if (parameterTypes.length != jdbcTypes.size()/* 列数 */) {
      return false;
    }

    /*

    2、构造器参数个数，和ResultSet中的列数相等，则进一步判断，是否存在每个"构造器参数类型"和"该参数索引位置的列的jdbc类型"所对应的TypeHandler，
    只要有一个参数没有对应的typeHandler，则代表当前构造器不符合

    */

    for (int i = 0; i < parameterTypes.length; i++) {
      // 根据"构造器参数类型"和"对应索引列的jdbc类型"，获取typeHandler，如果不存在，则代表该构造器不适用
      // jdbcTypes.get(i)：从这行代码可以看出，按照当前方式查找到的适用的构造器，其构造器的参数顺序，与查询到的数据列的顺序需要一一对其才行！
      if (!typeHandlerRegistry.hasTypeHandler(parameterTypes[i]/* 参数类型 */, jdbcTypes.get(i)/* 对应列的jdbc类型 */)) {
        return false;
      }
    }

    /* 3、当前构造器符合，则返回true —— 构造器参数个数，和ResultSet中的列数相等；并且存在每个"构造器参数类型"和"该参数类型索引位置所对应的列的jdbc类型"的TypeHandler */

    return true;
  }

  /**
   * 通过构造器自动映射的方式，得出构造器参数值，然后通过构造器参数类型和参数值，创建结果对象。
   *
   * 构造器自动映射的方式，得出构造器参数值，一共有2种：
   * （1）根据"构造器参数顺序"和"ResultSet中列的顺序"一一对应，得出构造器参数对应的参数值
   * （2）根据"构造器参数名"和"ResultSet中的列名"相匹配，得出构造器参数对应的参数值
   *
   * 题外：构造器自动映射方式，就是如何获取构造器参数类型和参数值的方式
   */
  private Object applyConstructorAutomapping(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix,
                                             Class<?> resultType, List<Class<?>> constructorArgTypes,
                                             List<Object> constructorArgs, Constructor<?> constructor) throws SQLException {
    boolean foundValues = false;

    /*

    1、通过构造器自动映射的方式，获取构造器参数类型和参数值
    题外：一共有两种构造器自动映射的方式（也就是：两种获取构造器参数类型和参数值的方式）

    */

    // 默认值false
    if (configuration.isArgNameBasedConstructorAutoMapping/* 是基于Arg名称的构造函数自动映射 */()) {
      // （1）根据"构造器参数顺序"和"ResultSet中列的顺序"一一对应，得出构造器参数对应的参数值
      foundValues = applyArgNameBasedConstructorAutoMapping/* 应用基于Arg名称的构造函数自动映射 */(rsw, resultMap, columnPrefix,
        resultType, constructorArgTypes, constructorArgs,
        constructor, foundValues);
    } else {
      // （2）根据"构造器参数名"和"ResultSet中的列名"相匹配，得出构造器参数对应的参数值
      foundValues = applyColumnOrderBasedConstructorAutomapping/* 应用基于列顺序的构造函数自动映射 */(rsw, constructorArgTypes, constructorArgs, constructor,
        foundValues);
    }

    /* 2、通过获取到对应的构造器参数类型和参数值，实例化对象 */
    return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
  }

  /**
   * 根据"构造器参数顺序"和"ResultSet中列的顺序"一一对应，得出构造器参数对应的参数值
   *
   * @param rsw
   * @param constructorArgTypes
   * @param constructorArgs
   * @param constructor
   * @param foundValues
   * @return
   * @throws SQLException
   */
  private boolean applyColumnOrderBasedConstructorAutomapping(ResultSetWrapper rsw, List<Class<?>> constructorArgTypes,
                                                              List<Object> constructorArgs, Constructor<?> constructor, boolean foundValues) throws SQLException {
    // 遍历构造器参数类型
    for (int i = 0; i < constructor.getParameterTypes().length; i++) {

      /* 1、获取构造器参数类型 */
      // 获取构造器参数类型
      Class<?> parameterType = constructor.getParameterTypes()[i];

      /* 2、获取构造器参数值 */
      // 从ResultSet中获取"构造器参数索引"对应位置的"列名"
      String columnName = rsw.getColumnNames().get(i);
      // 获取TypeHandler
      TypeHandler<?> typeHandler = rsw.getTypeHandler(parameterType, columnName);
      // 通过TypeHandler获取当前行中，当前列名对应的数据，作为构造器参数值
      Object value = typeHandler.getResult(rsw.getResultSet(), columnName);

      /* 3、添加构造器参数类型 */
      constructorArgTypes.add(parameterType);
      /* 4、添加构造器参数值 */
      constructorArgs.add(value);

      foundValues = value != null || foundValues;
    }
    return foundValues;
  }

  /**
   * 根据"构造器参数名"和"ResultSet中的列名"相匹配，得出构造器参数对应的参数值
   *
   * @param rsw
   * @param resultMap
   * @param columnPrefix
   * @param resultType
   * @param constructorArgTypes
   * @param constructorArgs
   * @param constructor
   * @param foundValues
   * @return
   * @throws SQLException
   */
  private boolean applyArgNameBasedConstructorAutoMapping(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix, Class<?> resultType,
                                                          List<Class<?>> constructorArgTypes, List<Object> constructorArgs, Constructor<?> constructor, boolean foundValues)
    throws SQLException {
    List<String> missingArgs = null;
    // 获取构造器参数
    Parameter[] params = constructor.getParameters();
    // 遍历构造器参数
    for (Parameter param : params) {
      boolean columnNotFound = true;
      // 获取构造器参数上标注的@Param
      Param paramAnno = param.getAnnotation(Param.class);

      /* 参数名称：如果构造器上标注了@Param，则获取@Param中设置的名称作为构造器参数名称；否则获取参数名称 */
      String paramName = paramAnno == null ? param.getName()/* 获取参数名称 */ : paramAnno.value()/* 获取@Param中设置的参数名称 */;

      // 遍历列名
      for (String columnName : rsw.getColumnNames()) {

        // 判断参数名和列名是否相等（参数名和列名相等，则返回true）
        if (columnMatchesParam/* 列匹配参数 */(columnName/* 列名 */, paramName/* 参数名 */, columnPrefix)) {
          /* 构造器参数类型 */
          Class<?> paramType = param.getType();

          /* 构造器参数值 */
          // 获取TypeHandler
          TypeHandler<?> typeHandler = rsw.getTypeHandler(paramType, columnName);
          // 通过TypeHandler获取当前行中，当前列名对应的数据，作为构造器参数值
          Object value = typeHandler.getResult(rsw.getResultSet(), columnName);

          /* 添加构造器参数类型 */
          constructorArgTypes.add(paramType);
          /* 添加构造器参数值 */
          constructorArgs.add(value);

          final String mapKey = resultMap.getId() + ":" + columnPrefix;
          if (!autoMappingsCache.containsKey(mapKey)) {
            MapUtil.computeIfAbsent(constructorAutoMappingColumns, mapKey, k -> new ArrayList<>()).add(columnName);
          }

          columnNotFound = false;
          foundValues = value != null || foundValues;
        }

      }

      if (columnNotFound) {
        if (missingArgs == null) {
          missingArgs = new ArrayList<>();
        }
        missingArgs.add(paramName);
      }

    }

    // 没有找到构造器参数值，并且构造器参数值的个数小于构造器参数的个数，则报错
    if (foundValues && constructorArgs.size() < params.length) {
      throw new ExecutorException(MessageFormat.format("Constructor auto-mapping of ''{1}'' failed "
          + "because ''{0}'' were not found in the result set; "
          + "Available columns are ''{2}'' and mapUnderscoreToCamelCase is ''{3}''.",
        missingArgs, constructor, rsw.getColumnNames(), configuration.isMapUnderscoreToCamelCase()));
    }

    return foundValues;
  }

  /**
   * 列名匹配参数名：参数名和列名相等，则返回true
   *
   * @param columnName          列名
   * @param paramName           参数名
   * @param columnPrefix        列名前缀
   * @return
   */
  private boolean columnMatchesParam(String columnName, String paramName, String columnPrefix) {
    if (columnPrefix != null) {
      // 如果存在列名前缀，但是列名却不是以"列名前缀"开头，则直接返回false，代表当前列不匹配
      if (!columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
        return false;
      }
      // 列名存在当前列名前缀，则去掉列名前缀
      columnName = columnName.substring(columnPrefix.length());
    }

    // 参数名和列名相等，则返回true
    return paramName
      .equalsIgnoreCase(configuration.isMapUnderscoreToCamelCase()/* 是否是下划线到驼峰的映射 */ ? columnName.replace("_", "") : columnName);
  }

  /**
   * 通过TypeHandler，提取ResultSet当中当前行数据，构建resultType类型的结果对象，进行返回
   *
   * 题外：这也就是为什么，我们的返回值类型是int的时候，不需要配置映射，mybatis会自动帮我们返回值的原因！
   */
  private Object createPrimitiveResultObject(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix) throws SQLException {
    /* 1、结果类型（ResultMap的type属性） */
    final Class<?> resultType = resultMap.getType();

    /* 2、列名 */
    final String columnName;

    /* 2.1、存在结果映射，就直接获取第一个结果映射中配置的数据库列名 */
    // 存在结果映射(resultMappings)
    if (!resultMap.getResultMappings().isEmpty()) {
      final List<ResultMapping> resultMappingList = resultMap.getResultMappings();
      // 获取第一个结果映射
      final ResultMapping mapping = resultMappingList.get(0);
      // 获取结果映射中配置的数据库列名
      columnName = prependPrefix(mapping.getColumn(), columnPrefix);
    }
    /* 2.2、不存在结果映射，就直接获取ResultSet中第一列的列名 */
    // 不存在结果映射(resultMappings)
    else {
      columnName = rsw.getColumnNames().get(0);
    }

    /* 3、获取结果类型对应的TypeHandler */
    final TypeHandler<?> typeHandler = rsw.getTypeHandler(resultType, columnName);

    /*

    4、通过TypeHandler，提取ResultSet当中当前行数据，构建resultType类型的结果对象，进行返回

    题外：这也就是为什么，我们的返回值类型是int的时候，不需要配置映射，mybatis会自动帮我们返回值的原因！

    */
    return typeHandler.getResult(rsw.getResultSet(), columnName);
  }

  //
  // NESTED QUERY —— 嵌套查询
  //

  /**
   * 发起嵌套查询，得到嵌套查询的结果（从ResultSet中获取对应列值，作为嵌套sql语句的sql参数值；然后发起查询，获取结果）
   *
   * @param rs
   * @param constructorMapping
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object getNestedQueryConstructorValue(ResultSet rs, ResultMapping constructorMapping, String columnPrefix) throws SQLException {
    /**
     * <constructor>
     *   <arg column="id" javaType="int"/>
     *   <arg column="username" javaType="string" select=""/>
     * </constructor>
     */
    // 获取嵌套查询id
    final String nestedQueryId/* 嵌套查询id */ = constructorMapping.getNestedQueryId();

    // 从configuration.mappedStatements中，获取嵌套查询id对应的MappedStatement
    final MappedStatement nestedQuery = configuration.getMappedStatement(nestedQueryId);

    // 获取嵌套查询需要的sql参数类型
    // 题外：<select parameterMap="">中的parameterMap属性
    final Class<?> nestedQueryParameterType = nestedQuery.getParameterMap().getType();

    // 从ResultSet中，获取"嵌套查询sql语句"所需要的"sql实参对象"，里面包含了"sql参数值"
    final Object nestedQueryParameterObject = prepareParameterForNestedQuery/* 为嵌套查询准备参数 */(rs, constructorMapping, nestedQueryParameterType, columnPrefix);

    Object value = null;
    if (nestedQueryParameterObject != null) {
      // BoundSql
      // 构建jdbc可执行sql，和构建sql参数映射（sql参数名称，以及在参数对象中的属性类型）
      final BoundSql nestedBoundSql = nestedQuery.getBoundSql(nestedQueryParameterObject/* 参数对象 */);
      // CacheKey
      final CacheKey key = executor.createCacheKey(nestedQuery, nestedQueryParameterObject, RowBounds.DEFAULT, nestedBoundSql);
      // 目标类型，例如：<arg column="id" javaType="int"/>中的int
      final Class<?> targetType = constructorMapping.getJavaType();
      // ResultLoader
      final ResultLoader resultLoader = new ResultLoader/* 结果加载器 */(configuration, executor, nestedQuery, nestedQueryParameterObject, targetType, key, nestedBoundSql);
      // 发起查询，和获取查询到的结果
      value = resultLoader.loadResult();
    }

    return value;
  }

  /**
   * 获取嵌套查询的结果
   *
   * 1、先看下一级缓存中是否有嵌套查询语句的CacheKey的结果。如果一级缓存中有嵌套查询语句的CacheKey的结果：
   * （1）继续判断，如果一级缓存中存在要查询语句的CacheKey的数据，且不是"执行占位符"，则直接从一级缓存中获取数据，作为属性值，设置到结果对象中；
   *  题外：这里的判断，比上一个判断，多了，判断一级缓存中结果不是"执行占位符"这个操作，因为有可能一级缓存中对应的嵌套查询语句的CacheKey的结果是"执行占位符"，而这不是实际数据！
   * （2）如果一级缓存中不存在，就将创建的DeferredLoad对象，放入deferredLoads(延迟加载队列)中，用于后续延迟加载
   * 2、如果一级缓存中没有嵌套查询语句的CacheKey的结果，则创建嵌套查询语句对应的ResultLoader(结果加载器)
   * 2.1、如果是延迟加载的，就把ResultLoader(结果加载器)，放入到ResultLoaderMap中
   * 2.2、如果不是延迟加载，则通过ResultLoader，立即发起查询，获取结果
   */
  private Object getNestedQueryMappingValue(ResultSet rs, MetaObject metaResultObject, ResultMapping propertyMapping, ResultLoaderMap lazyLoader, String columnPrefix)
    throws SQLException {

    // 嵌套查询id
    final String nestedQueryId = propertyMapping.getNestedQueryId();
    // 属性名
    final String property = propertyMapping.getProperty();
    // 通过"嵌套查询id"得出MappedStatement
    final MappedStatement nestedQuery = configuration.getMappedStatement(nestedQueryId);
    // 获取嵌套查询所需的sql参数类型
    final Class<?> nestedQueryParameterType = nestedQuery.getParameterMap().getType();

    // 准备嵌套查询语句所需要的sql参数对象
    final Object nestedQueryParameterObject = prepareParameterForNestedQuery(rs, propertyMapping, nestedQueryParameterType, columnPrefix);

    // 嵌套查询的结果
    Object value = null;

    // 必须得要存在sql参数对象，才会发起查询
    if (nestedQueryParameterObject != null) {
      // 获取jdbc可执行的sql
      final BoundSql nestedBoundSql = nestedQuery.getBoundSql(nestedQueryParameterObject);
      // CacheKey
      final CacheKey key = executor.createCacheKey(nestedQuery, nestedQueryParameterObject, RowBounds.DEFAULT, nestedBoundSql);
      /**
       * 例如：
       * 如果未配置javaType，此时的targetType，就是Account中的user属性名对应的User类型；
       * 如果配置了javaType，
       *
       * <resultMap id="accountMap" type="com.hm.m_04.entity.Account">
       *   <association property="user" javaType="com.hm.m_04.entity.User"
       *                select="com.hm.m_04.dao.UserDao.findById" column="uid"></association>
       * </resultMap>
       */
      // java类型（结果对象中当前属性名对应的属性类型）
      final Class<?> targetType = propertyMapping.getJavaType();

      /*

      1、先看下一级缓存中是否有嵌套查询语句的结果。如果一级缓存中有嵌套查询语句的CacheKey的结果：
      （1）继续判断，如果一级缓存中存在要查询语句的CacheKey的数据，且不是"执行占位符"，则直接从一级缓存中获取数据，作为属性值，设置到结果对象中；
       题外：这里的判断，比上一个判断，多了，判断一级缓存中结果不是"执行占位符"这个操作，因为有可能一级缓存中对应的嵌套查询语句的CacheKey的结果是"执行占位符"，而这不是实际数据！
      （2）如果一级缓存中不存在，就将创建的DeferredLoad对象，放入deferredLoads(延迟加载队列)中，用于后续延迟加载

      */
      if (executor.isCached/* 已缓存 */(nestedQuery, key)) {
        // 一级缓存中有就立即加载；一级缓存中没有就放入延迟加载队列，延迟加载：
        // (1)如果一级缓存中存在要查询语句的CacheKey的数据，且不是"执行占位符"，则直接从一级缓存中获取数据，作为属性值，设置到结果对象中；
        // (2)如果一级缓存中不存在，就将创建的DeferredLoad对象，放入deferredLoads(延迟加载队列)中，用于后续延迟加载
        // CachingExecutor
        executor.deferLoad/* 延迟加载 */(nestedQuery, metaResultObject, property, key, targetType);
        value = DEFERRED/* 延迟 */;
      }
      /* 2、如果一级缓存中没有嵌套查询语句的CacheKey的结果，则创建嵌套查询语句对应的ResultLoader(结果加载器) */
      // 如果一级缓存中不存在嵌套查询语句的结果
      else {
        final ResultLoader resultLoader/* 结果加载器 */ = new ResultLoader(configuration, executor, nestedQuery, nestedQueryParameterObject, targetType, key, nestedBoundSql);
        /* 2.1、如果是延迟加载的，就把ResultLoader(结果加载器)，放入到ResultLoaderMap中 */
        // 是延迟加载的
        if (propertyMapping.isLazy()) {
          // ⚠️把ResultLoader(结果加载器)，放入到ResultLoaderMap中
          lazyLoader.addLoader(property, metaResultObject, resultLoader);
          /**
           * ⚠️DEFERRED：作用：代表延迟加载属性值的标识，不代理属性值。
           * >>> 在后续设置属性值的步骤中，看到属性值是DEFERRED，也就是说目前还不存在属性值，就会跳过设置属性值（不会往结果对象中设置当前属性名的属性值）；
           * >>> 等待后续触发延迟加载条件时，才会通过代理对象，加载属性值，以及设置属性值到结果对象中！
           */
          value = DEFERRED/* 延迟 */;
        }
        /* 2.2、如果不是延迟加载，则通过ResultLoader，立即发起查询，获取结果，作为属性值返回 */
        // 立即加载
        else {
          // 发起查询和获取结果
          value = resultLoader.loadResult();
        }
      }
    }

    return value;
  }

  /**
   * 准备嵌套查询语句所需要的sql参数对象
   */
  private Object prepareParameterForNestedQuery(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType/* 嵌套查询的类型 */, String columnPrefix) throws SQLException {
    /* 1、当前resultMapping中存在复合列名 */
    if (resultMapping.isCompositeResult()) {
      // 构建复合列名对应的"sql实参数"对象，里面包含了sql参数值
      return prepareCompositeKeyParameter/* 准备复合关键参数 */(rs, resultMapping, parameterType, columnPrefix);
    }
    /* 2、当前resultMapping中，不存在复合列名 */
    else {
      // 获取当前行中，指定列名的数据，作为sql实参对象，此时sql实参数对象就是作为sql参数值
      return prepareSimpleKeyParameter/* 准备简单的关键参数 */(rs, resultMapping, parameterType, columnPrefix);
    }
  }

  /**
   *
   * @param rs
   * @param resultMapping
   * @param parameterType       嵌套查询的类型（sql实参数对象类型）
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object prepareSimpleKeyParameter(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
    /* 获取sql实参对象类型，对应的TypeHandler */
    final TypeHandler<?> typeHandler;
    if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
      typeHandler = typeHandlerRegistry.getTypeHandler(parameterType);
    } else {
      typeHandler = typeHandlerRegistry.getUnknownTypeHandler();
    }

    /* 通过TypeHandler，从ResultSet，获取当前行中，指定列名的数据，作为sql实参对象 */
    return typeHandler.getResult(rs, prependPrefix(resultMapping.getColumn()/* 列名 */, columnPrefix));
  }

  /**
   * 构建复合列名对应的"sql实参数"对象
   */
  private Object prepareCompositeKeyParameter(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
    // ⚠️创建一个空壳的sql实参对象
    final Object parameterObject = instantiateParameterObject/* 实例化参数对象 */(parameterType);
    // 创建sql参数对象对应的MetaObject（元数据对象）
    final MetaObject metaObject = configuration.newMetaObject(parameterObject);

    boolean foundValues = false;

    // 遍历复合列名中的每一个列
    for (ResultMapping innerResultMapping : resultMapping.getComposites()) {
      // ⚠️获取属性类型
      final Class<?> propType = metaObject.getSetterType(innerResultMapping.getProperty()/* 属性名 */);

      // 获取属性类型对应的TypeHandler
      final TypeHandler<?> typeHandler = typeHandlerRegistry.getTypeHandler(propType);

      // ⚠️通过属性类型对应的TypeHandler，从ResultSet，获取当前行中，指定列名的数据，作为属性值
      final Object propValue = typeHandler.getResult(rs, prependPrefix(innerResultMapping.getColumn()/* 列名 */, columnPrefix));

      // ⚠️设置sql实参对象中的属性值
      // issue #353 & #560 do not execute nested query if key is null —— 问题353和560，如果键为空，则不执行嵌套查询
      if (propValue != null) {
        metaObject.setValue(innerResultMapping.getProperty()/* 属性名 */, propValue/* 属性值 */);
        foundValues = true;
      }
    }

    // 返回"sql实参对象"
    return foundValues ? parameterObject : null;
  }

  private Object instantiateParameterObject(Class<?> parameterType) {
    if (parameterType == null) {
      return new HashMap<>();
    } else if (ParamMap.class.equals(parameterType)) {
      return new HashMap<>(); // issue #649
    } else {
      return objectFactory.create(parameterType);
    }
  }

  //
  // DISCRIMINATOR —— 鉴别器
  //

  /**
   * 处理ResultMap中的鉴别器：从ResultSet中获取鉴别值，然后根据鉴别值，从鉴别器中选择出对应的映射使用的ResultMap。
   * 如果不存在鉴别器，或者未能从鉴别器当中获取到ResultMap，则返回原先的ResultMap
   *
   * @param rs
   * @param resultMap
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  public ResultMap resolveDiscriminatedResultMap(ResultSet rs, ResultMap resultMap, String columnPrefix) throws SQLException {
    // 记录鉴别器已经选择到的resultMapId
    Set<String> pastDiscriminators = new HashSet<>();

    /* 1、获取ResultMap中配置的鉴别器 */
    /**
     * 例如：
     * <discriminator javaType="String" column="sex">
     *    <!-- 男性 -->
     *    <case value="1" resultMap="HealthReportMaleResultMap"/>
     *    <!-- 女性 -->
     *    <case value="0" resultMap="HealthReportFemale"/>
     * </discriminator>
     */
    // 获取鉴别器
    Discriminator discriminator = resultMap.getDiscriminator();

    // 循环处理鉴别器
    while (discriminator != null) {

      /* 2、从ResultSet中，获取鉴别值 */
      // 从ResultSet中，获取要鉴别的列的数据
      // 例如：<discriminator javaType="String" column="sex">，就是从ResultSet中获取"sex"这一列数据
      final Object value = getDiscriminatorValue(rs, discriminator, columnPrefix);

      /* 3、根据鉴别值，去鉴别器中，获取对应的resultMapId */
      /**
       * 例如：
       * <discriminator javaType="String" column="sex">
       *    <!-- 男性 -->
       *    <case value="1" resultMap="HealthReportMaleResultMap"/>
       *    <!-- 女性 -->
       *    <case value="0" resultMap="HealthReportFemale"/>
       * </discriminator>
       *
       * 当前命名空间是：com.msb.other.discriminator.dao.UserDao
       * 要鉴别的列的值；sex=1，
       * 那么获取到的resultMapId是：com.msb.other.discriminator.dao.UserDao.HealthReportMaleResultMap
       *
       */
      // 根据要鉴别的列的值，获取对应的resultMapId
      final String discriminatedMapId = discriminator.getMapIdFor(String.valueOf(value));

      // 判断configuration.resultMaps中是否包含"根据鉴别值，得到的resultMapId"
      if (configuration.hasResultMap(discriminatedMapId)) {

        /*

        4、从configuration.resultMaps中获取，"根据鉴别值，得到的resultMapId"，对应的resultMap

        注意：⚠️只是返回一个resultMap，下面会继续处理嵌套的鉴别器，所以由此得之，只会返回最后一个鉴别器所选择到的resultMap

        */

        // 如果包含，则从configuration.resultMaps中获取，"根据鉴别值，得到的resultMapId"，对应的resultMap
        resultMap = configuration.getResultMap(discriminatedMapId);

        // 记录当前鉴别器作为最后一个鉴别器
        Discriminator lastDiscriminator = discriminator;

        /* 5、从鉴别器选择的ResultMap中，继续获取鉴别器，看下是否还嵌套了鉴别器，有的话就继续处理嵌套的鉴别器 */
        // 从鉴别器选择的ResultMap中，继续获取鉴别器，看下是否还嵌套了鉴别器，有的话就继续处理嵌套的鉴别器
        // 因为鉴别器指向的ResultMap中，可以继续配置鉴别器！
        discriminator = resultMap.getDiscriminator();

        /**
         * 1、pastDiscriminators.add(discriminatedMapId)：添加鉴别器。
         * 如果能添加，则证明是之前不存在的，返回true；
         * 如果添加失败，则证明是之前存在的，已经处理过的，则返回false，false取反为true，则代表，不需要继续往下进行处理了，要跳出while循环
         */
        // 如果当前鉴别器是最后一个鉴别器 || 当前鉴别器选择的resultMapId，已经处理过了，则跳过
        if (discriminator == lastDiscriminator || !pastDiscriminators.add(discriminatedMapId)) {
          break;
        }
      } else {
        break;
      }
    }

    // 返回resultMap
    return resultMap;
  }

  /**
   * 从ResultSet中，获取要鉴别的列的数据，
   * 例如：<discriminator javaType="String" column="sex">，就是从ResultSet中获取"sex"这一列数据
   *
   * 参考：
   * <discriminator javaType="String" column="sex">
   *   <!-- 男性 -->
   *   <case value="1" resultMap="HealthReportMaleResultMap"/>
   *   <!-- 女性 -->
   *   <case value="0" resultMap="HealthReportFemale"/>
   * </discriminator>
   *
   * @param rs
   * @param discriminator 鉴别器
   * @param columnPrefix  列名前缀
   * @return
   * @throws SQLException
   */
  private Object getDiscriminatorValue(ResultSet rs, Discriminator discriminator, String columnPrefix) throws SQLException {
    /* 1、获取<discriminator>标签信息 */
    // 获取<discriminator>标签信息，例如：<discriminator javaType="String" column="sex">
    final ResultMapping resultMapping = discriminator.getResultMapping();

    final TypeHandler<?> typeHandler = resultMapping.getTypeHandler();
    /* 2、从ResultSet中，获取要鉴别的列的数据 */
    /**
     * 1、resultMapping.getColumn()：获取鉴别器指定的数据库列名
     * 例如：<discriminator javaType="String" column="sex">中的column="sex"
     */
    // 从ResultSet中，获取要鉴别的列的数据，
    // 例如：<discriminator javaType="String" column="sex">，就是从ResultSet中获取"sex"这一列数据
    return typeHandler.getResult(rs, prependPrefix(resultMapping.getColumn(), columnPrefix));
  }

  /**
   * 为列名拼接上前缀，如果不存在前缀，就返回原始列名
   *
   * @param columnName    列名
   * @param prefix        前缀
   */
  private String prependPrefix(String columnName, String prefix) {
    if (columnName == null || columnName.length() == 0 || prefix == null || prefix.length() == 0) {
      return columnName;
    }
    return prefix + columnName;
  }

  //
  // HANDLE NESTED RESULT MAPS
  //

  private void handleRowValuesForNestedResultMap(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler<?> resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
    final DefaultResultContext<Object> resultContext = new DefaultResultContext<>();
    ResultSet resultSet = rsw.getResultSet();
    skipRows(resultSet, rowBounds);
    Object rowValue = previousRowValue;
    while (shouldProcessMoreRows(resultContext, rowBounds) && !resultSet.isClosed() && resultSet.next()) {
      final ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(resultSet, resultMap, null);
      final CacheKey rowKey = createRowKey(discriminatedResultMap, rsw, null);
      Object partialObject = nestedResultObjects.get(rowKey);
      // issue #577 && #542
      if (mappedStatement.isResultOrdered()) {
        if (partialObject == null && rowValue != null) {
          nestedResultObjects.clear();
          storeObject(resultHandler, resultContext, rowValue, parentMapping, resultSet);
        }
        rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, null, partialObject);
      } else {
        rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, null, partialObject);
        if (partialObject == null) {
          storeObject(resultHandler, resultContext, rowValue, parentMapping, resultSet);
        }
      }
    }
    if (rowValue != null && mappedStatement.isResultOrdered() && shouldProcessMoreRows(resultContext, rowBounds)) {
      storeObject(resultHandler, resultContext, rowValue, parentMapping, resultSet);
      previousRowValue = null;
    } else if (rowValue != null) {
      previousRowValue = rowValue;
    }
  }

  //
  // NESTED RESULT MAP (JOIN MAPPING)
  //

  private boolean applyNestedResultMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String parentPrefix, CacheKey parentRowKey, boolean newObject) {
    boolean foundValues = false;
    for (ResultMapping resultMapping : resultMap.getPropertyResultMappings()) {
      final String nestedResultMapId = resultMapping.getNestedResultMapId();
      if (nestedResultMapId != null && resultMapping.getResultSet() == null) {
        try {
          final String columnPrefix = getColumnPrefix(parentPrefix, resultMapping);
          final ResultMap nestedResultMap = getNestedResultMap(rsw.getResultSet(), nestedResultMapId, columnPrefix);
          if (resultMapping.getColumnPrefix() == null) {
            // try to fill circular reference only when columnPrefix
            // is not specified for the nested result map (issue #215)
            Object ancestorObject = ancestorObjects.get(nestedResultMapId);
            if (ancestorObject != null) {
              if (newObject) {
                linkObjects(metaObject, resultMapping, ancestorObject); // issue #385
              }
              continue;
            }
          }
          final CacheKey rowKey = createRowKey(nestedResultMap, rsw, columnPrefix);
          final CacheKey combinedKey = combineKeys(rowKey, parentRowKey);
          Object rowValue = nestedResultObjects.get(combinedKey);
          boolean knownValue = rowValue != null;
          instantiateCollectionPropertyIfAppropriate(resultMapping, metaObject); // mandatory
          if (anyNotNullColumnHasValue(resultMapping, columnPrefix, rsw)) {
            rowValue = getRowValue(rsw, nestedResultMap, combinedKey, columnPrefix, rowValue);
            if (rowValue != null && !knownValue) {
              linkObjects(metaObject, resultMapping, rowValue);
              foundValues = true;
            }
          }
        } catch (SQLException e) {
          throw new ExecutorException("Error getting nested result map values for '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
        }
      }
    }
    return foundValues;
  }

  private String getColumnPrefix(String parentPrefix, ResultMapping resultMapping) {
    final StringBuilder columnPrefixBuilder = new StringBuilder();
    if (parentPrefix != null) {
      columnPrefixBuilder.append(parentPrefix);
    }
    if (resultMapping.getColumnPrefix() != null) {
      columnPrefixBuilder.append(resultMapping.getColumnPrefix());
    }
    return columnPrefixBuilder.length() == 0 ? null : columnPrefixBuilder.toString().toUpperCase(Locale.ENGLISH);
  }

  private boolean anyNotNullColumnHasValue(ResultMapping resultMapping, String columnPrefix, ResultSetWrapper rsw) throws SQLException {
    Set<String> notNullColumns = resultMapping.getNotNullColumns();
    if (notNullColumns != null && !notNullColumns.isEmpty()) {
      ResultSet rs = rsw.getResultSet();
      for (String column : notNullColumns) {
        rs.getObject(prependPrefix(column, columnPrefix));
        if (!rs.wasNull()) {
          return true;
        }
      }
      return false;
    } else if (columnPrefix != null) {
      for (String columnName : rsw.getColumnNames()) {
        if (columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix.toUpperCase(Locale.ENGLISH))) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  private ResultMap getNestedResultMap(ResultSet rs, String nestedResultMapId, String columnPrefix) throws SQLException {
    ResultMap nestedResultMap = configuration.getResultMap(nestedResultMapId);
    return resolveDiscriminatedResultMap(rs, nestedResultMap, columnPrefix);
  }

  //
  // UNIQUE RESULT KEY
  //

  private CacheKey createRowKey(ResultMap resultMap, ResultSetWrapper rsw, String columnPrefix) throws SQLException {
    final CacheKey cacheKey = new CacheKey();
    cacheKey.update(resultMap.getId());
    List<ResultMapping> resultMappings = getResultMappingsForRowKey(resultMap);
    if (resultMappings.isEmpty()) {
      if (Map.class.isAssignableFrom(resultMap.getType())) {
        createRowKeyForMap(rsw, cacheKey);
      } else {
        createRowKeyForUnmappedProperties(resultMap, rsw, cacheKey, columnPrefix);
      }
    } else {
      createRowKeyForMappedProperties(resultMap, rsw, cacheKey, resultMappings, columnPrefix);
    }
    if (cacheKey.getUpdateCount() < 2) {
      return CacheKey.NULL_CACHE_KEY;
    }
    return cacheKey;
  }

  private CacheKey combineKeys(CacheKey rowKey, CacheKey parentRowKey) {
    if (rowKey.getUpdateCount() > 1 && parentRowKey.getUpdateCount() > 1) {
      CacheKey combinedKey;
      try {
        combinedKey = rowKey.clone();
      } catch (CloneNotSupportedException e) {
        throw new ExecutorException("Error cloning cache key.  Cause: " + e, e);
      }
      combinedKey.update(parentRowKey);
      return combinedKey;
    }
    return CacheKey.NULL_CACHE_KEY;
  }

  private List<ResultMapping> getResultMappingsForRowKey(ResultMap resultMap) {
    List<ResultMapping> resultMappings = resultMap.getIdResultMappings();
    if (resultMappings.isEmpty()) {
      resultMappings = resultMap.getPropertyResultMappings();
    }
    return resultMappings;
  }

  private void createRowKeyForMappedProperties(ResultMap resultMap, ResultSetWrapper rsw, CacheKey cacheKey, List<ResultMapping> resultMappings, String columnPrefix) throws SQLException {
    for (ResultMapping resultMapping : resultMappings) {
      if (resultMapping.isSimple()) {
        final String column = prependPrefix(resultMapping.getColumn(), columnPrefix);
        final TypeHandler<?> th = resultMapping.getTypeHandler();
        List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);
        // Issue #114
        if (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH))) {
          final Object value = th.getResult(rsw.getResultSet(), column);
          if (value != null || configuration.isReturnInstanceForEmptyRow()) {
            cacheKey.update(column);
            cacheKey.update(value);
          }
        }
      }
    }
  }

  private void createRowKeyForUnmappedProperties(ResultMap resultMap, ResultSetWrapper rsw, CacheKey cacheKey, String columnPrefix) throws SQLException {
    final MetaClass metaType = MetaClass.forClass(resultMap.getType(), reflectorFactory);
    List<String> unmappedColumnNames = rsw.getUnmappedColumnNames(resultMap, columnPrefix);
    for (String column : unmappedColumnNames) {
      String property = column;
      if (columnPrefix != null && !columnPrefix.isEmpty()) {
        // When columnPrefix is specified, ignore columns without the prefix.
        if (column.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
          property = column.substring(columnPrefix.length());
        } else {
          continue;
        }
      }
      if (metaType.findProperty(property, configuration.isMapUnderscoreToCamelCase()) != null) {
        String value = rsw.getResultSet().getString(column);
        if (value != null) {
          cacheKey.update(column);
          cacheKey.update(value);
        }
      }
    }
  }

  private void createRowKeyForMap(ResultSetWrapper rsw, CacheKey cacheKey) throws SQLException {
    List<String> columnNames = rsw.getColumnNames();
    for (String columnName : columnNames) {
      final String value = rsw.getResultSet().getString(columnName);
      if (value != null) {
        cacheKey.update(columnName);
        cacheKey.update(value);
      }
    }
  }

  /**
   * 把当前ResultSet行结果，与原先的ResultSet行结果进行链接
   *
   * @param metaObject            单行结果对象的metaObject
   * @param resultMapping         单行结果对象中某个属性的ResultMapping（也就是，当前"resultSet属性""所在标签的ResultMapping"）
   * @param rowValue              当前值 —— resultSet属性值所引入的ResultSet的行结果对象
   */
  private void linkObjects(MetaObject metaObject, ResultMapping resultMapping, Object rowValue) {
    /* 1、属性是集合类型，则为原先对象创建集合对象，然后往集合对象中添加当前值 */
    // 如果是集合类型，实例化集合对象，并设置到结果对象当中；然后返回刚刚实例化的集合对象
    final Object collectionProperty = instantiateCollectionPropertyIfAppropriate/* 如果合适，实例化集合属性 */(resultMapping, metaObject);
    // 是集合类型
    if (collectionProperty != null) {
      // 创建集合对象的MetaObject
      final MetaObject targetMetaObject = configuration.newMetaObject(collectionProperty);
      // 往集合中设置值
      targetMetaObject.add(rowValue);
    }
    /* 2、属性非集合类型，则往原先的结果对象中设置当前值 */
    // 不是集合类型
    else {
      metaObject.setValue(resultMapping.getProperty(), rowValue);
    }
  }

  /**
   * 如果是集合类型，实例化集合对象，并设置到结果对象当中
   *
   * @param metaObject            单行结果对象的metaObject
   * @param resultMapping         单行结果对象中某个属性的ResultMapping（也就是，当前"resultSet属性""所在标签的ResultMapping"）
   */
  private Object instantiateCollectionPropertyIfAppropriate(ResultMapping resultMapping, MetaObject metaObject) {
    // 属性名
    final String propertyName = resultMapping.getProperty();
    // 属性值
    Object propertyValue = metaObject.getValue(propertyName);

    // 不存在属性值
    if (propertyValue == null) {
      // java类型，也就是属性值类型
      Class<?> type = resultMapping.getJavaType();

      // 未配置属性值类型，则通过属性名，去结果对象中获取对应的属性类型
      if (type == null) {
        type = metaObject.getSetterType(propertyName);
      }

      try {
        // 是集合类型
        if (objectFactory.isCollection(type)) {
          // 创建集合对象
          propertyValue = objectFactory.create(type);
          // 往结对对象中，对应的属性名，设置集合对象
          metaObject.setValue(propertyName, propertyValue);

          return propertyValue;
        }
      } catch (Exception e) {
        throw new ExecutorException("Error instantiating collection property for result '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
      }
    }
    // 存在属性值，且属性值是一个集合，则返回这个集合对象（说明之前初始化好了）
    else if (objectFactory.isCollection(propertyValue.getClass())) {
      return propertyValue;
    }

    return null;
  }

  /**
   * 判断typeHandlerRegistry.typeHandlerMap中，是否存在resultType(结果类型)对应的TypeHandler
   */
  private boolean hasTypeHandlerForResultObject(ResultSetWrapper rsw, Class<?> resultType) {
    // 只存在一列
    if (rsw.getColumnNames().size() == 1) {
      /**
       * 1、rsw.getJdbcType(rsw.getColumnNames().get(0)：获取列名对应的jdbc类型
       */
      return typeHandlerRegistry.hasTypeHandler(resultType/* java类型 */, rsw.getJdbcType(rsw.getColumnNames().get(0)/* 获取第一个列名 */)/* jdbc类型 */);
    }
    return typeHandlerRegistry.hasTypeHandler(resultType/* java类型 */);
  }

}
