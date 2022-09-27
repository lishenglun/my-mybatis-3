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
package org.apache.ibatis.executor.resultset;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.ObjectTypeHandler;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * ResultSet包装器，丰富ResultSet方法，包含了ResultSet相关元数据
 *
 * @author Iwao AVE!
 */
public class ResultSetWrapper {

  // 结果集对象
  // 题外：JDBC中的ResultSet对象，代表原生结果集
  private final ResultSet resultSet;
  private final TypeHandlerRegistry typeHandlerRegistry;

  // 列名集合（或者叫字段名集合，记录了ResultSet中每列的列名，包括通过"as"关键字指定的列名）
  private final List<String> columnNames = new ArrayList<>();
  // 列对应的Java类型集合（字段对应的javaType类型名：记录ResultSet中每列对应的Java类型）
  private final List<String> classNames = new ArrayList<>();
  // 列对应的jdbc类型集合（记录了ResultSet中每列对应的JdbcType类型）
  private final List<JdbcType> jdbcTypes = new ArrayList<>();

  // 列名类型处理器集合（记录了每个列名对应的"java类型处理器集合"）
  // key：列名
  // value：java类型处理器集合
  // >>> key：java类型
  // >>> value：TypeHandler
  private final Map<String, Map<Class<?>, TypeHandler<?>>> typeHandlerMap = new HashMap<>();

  // 映射的列名集合（也就是记录了，resultMap中配置了映射，且在ResultSet中存在的列名）
  // key：mapKey(映射key) = resultMap.id:columnPrefix
  // value：ResultMap中配置了映射，且在ResultSet中存在的列名集合
  private final Map<String, List<String>> mappedColumnNamesMap = new HashMap<>();

  // 未映射的列名集合（也就是记录了，resultMap中未配置映射，但在ResultSet中存在的列名）
  // key：mapKey(映射key) = resultMap.id:columnPrefix
  // value：resultMap中未配置映射，但在ResultSet中存在的列名集合（ResultMap中未配置映射的列名集合）
  private final Map<String, List<String>> unMappedColumnNamesMap = new HashMap<>();

  public ResultSetWrapper(ResultSet rs, Configuration configuration) throws SQLException {
    super();
    /* 1、typeHandlerRegistry */
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();

    /* 2、resultSet */
    this.resultSet = rs;

    /* 3、获取列名、列的jdbc类型、列对应的Java类型 */
    // 获取ResultSet的元信息，通过ResultSetMetaData(JDBC)获取列信息
    final ResultSetMetaData metaData = rs.getMetaData();
    // ResultSet中的列数，总列数
    final int columnCount = metaData.getColumnCount();
    for (int i = 1; i <= columnCount; i++) {
      // 列名（获取列名或是通过"as"关键字指定的列名）
      columnNames.add(configuration.isUseColumnLabel() ? metaData.getColumnLabel(i) : metaData.getColumnName(i));

      // 列的jdbc类型（该列的Jdbc类型）
      jdbcTypes.add(JdbcType.forCode(metaData.getColumnType(i)));

      // 列对应的Java类型
      classNames.add(metaData.getColumnClassName(i));
    }
  }

  public ResultSet getResultSet() {
    return resultSet;
  }

  public List<String> getColumnNames() {
    return this.columnNames;
  }

  public List<String> getClassNames() {
    return Collections.unmodifiableList(classNames);
  }

  public List<JdbcType> getJdbcTypes() {
    return jdbcTypes;
  }

  /**
   * 获取列名对应的jdbc类型
   *
   * @param columnName 列名
   * @return 列名对应的jdbc类型
   */
  public JdbcType getJdbcType(String columnName) {
    // 遍历列名集合
    for (int i = 0; i < columnNames.size(); i++) {
      // 该索引位置下，有这个列名
      if (columnNames.get(i).equalsIgnoreCase(columnName)) {
        // 根据索引位置，获取列名对应的jdbc类型
        return jdbcTypes.get(i);
      }
    }
    return null;
  }

  /**
   * 获取TypeHandler
   * 1、先从当前对象的typeHandlerMap中，获取TypeHandler；
   * 2、如果无法从typeHandlerMap中获取handler，则去typeHandlerRegistry.typeHandlerMap中获取对应的TypeHandler
   * 3、如果从typeHandlerRegistry.typeHandlerMap中，也无法获取到对应的TypeHandler，则设置默认的TypeHandler为ObjectTypeHandler
   * <p>
   * Gets the type handler to use when reading the result set.
   * Tries to get from the TypeHandlerRegistry by searching for the property type.
   * If not found it gets the column JDBC type and tries to get a handler for it.
   *
   * @param propertyType the property type        java类型
   * @param columnName   the column name          列名
   * @return the type handler
   */
  public TypeHandler<?> getTypeHandler(Class<?> propertyType, String columnName) {
    TypeHandler<?> handler = null;

    /* 1、从当前对象的typeHandlerMap中，获取TypeHandler */

    // 1.1、从当前对象的typeHandlerMap中，获取列名对应的"java类型处理器集合"
    Map<Class<?>, TypeHandler<?>> columnHandlers = typeHandlerMap.get(columnName);

    // 1.2、如果列名对应的"java类型处理器集合"为空，则创建空的"java类型处理器集合"，放入到typeHandlerMap中
    if (columnHandlers == null) {
      // 创建空的"java类型处理器集合"
      columnHandlers = new HashMap<>();
      // ⚠️将空的"java类型处理器集合"，放入到typeHandlerMap中
      typeHandlerMap.put(columnName, columnHandlers);
    }
    // 1.3、如果列名对应的"java类型处理器集合"不为空，则从"java类型处理器集合"中获取"java类型"对应的TypeHandler
    else {
      handler = columnHandlers.get(propertyType);
    }

    /* 2、如果无法从typeHandlerMap中获取handler，则去typeHandlerRegistry.typeHandlerMap中获取对应的TypeHandler */
    if (handler == null) {

      /* （1）获取列名对应的jdbc类型 */
      JdbcType jdbcType = getJdbcType(columnName);

      /* （2）根据java类型和jdbc类型，去"java类型处理器集合"中获取对应的TypeHandler */
      // ⚠️属性类型作为java类型
      handler = typeHandlerRegistry.getTypeHandler(propertyType, jdbcType);

      // Replicate logic of UnknownTypeHandler#resolveTypeHandler —— 复制UnknownTypeHandler#resolveTypeHandler()的逻辑
      // See issue #59 comment 10 —— 见第59期评论 10
      if (handler == null || handler instanceof UnknownTypeHandler/* 未知类型处理程序 */) {

        // 获取列名对应的索引 —— 列索引
        final int index = columnNames.indexOf(columnName);

        /* （3）通过列索引，去"列对应的Java类型集合"中，获取对应的java类型 */
        // ⚠️列对应的Java类型，作为java类型
        final Class<?> javaType = resolveClass(classNames.get(index)/* 通过列索引，获取对应的java类型 */);

        /* （4）去typeHandlerRegistry.typeHandlerMap中获取对应的TypeHandler */
        if (javaType != null && jdbcType != null) {
          // 根据java类型和jdbc类型，去"java类型处理器集合"中获取对应的TypeHandler
          handler = typeHandlerRegistry.getTypeHandler(javaType, jdbcType);
        } else if (javaType != null) {
          // 根据java类型，去"java类型处理器集合"中获取对应的TypeHandler
          handler = typeHandlerRegistry.getTypeHandler(javaType);
        } else if (jdbcType != null) {
          // 根据jdbc类型，去"java类型处理器集合"中获取对应的TypeHandler
          handler = typeHandlerRegistry.getTypeHandler(jdbcType);
        }

      }

      /* 3、如果从typeHandlerRegistry.typeHandlerMap中，也无法获取到对应的TypeHandler，则设置默认的TypeHandler为ObjectTypeHandler */
      if (handler == null || handler instanceof UnknownTypeHandler) {
        // 指定默认的TypeHandler为ObjectTypeHandler
        handler = new ObjectTypeHandler();
      }

      /* 4、⚠️存放propertyType和TypeHandler的对应关系到当前对象的typeHandlerMap中 */
      columnHandlers.put(propertyType, handler);
    }

    return handler;
  }

  private Class<?> resolveClass(String className) {
    try {
      // #699 className could be null
      if (className != null) {
        return Resources.classForName(className);
      }
    } catch (ClassNotFoundException e) {
      // ignore
    }
    return null;
  }

  /**
   * 加载：1、ResultMap中配置了映射，且在ResultSet中存在的列名；2、和ResultMap中未配置映射，但在ResultSet中存在的列名
   *
   * @param resultMap
   * @param columnPrefix
   * @throws SQLException
   */
  private void loadMappedAndUnmappedColumnNames/* 加载映射和未映射的列名 */(ResultMap resultMap, String columnPrefix) throws SQLException {

    // ResultMap中配置了映射，且在ResultSet中存在的列名
    List<String> mappedColumnNames/* 映射的列名 */ = new ArrayList<>();
    // ResultMap中未配置映射，但在ResultSet中存在的列名
    List<String> unmappedColumnNames/* 未映射的列名 */ = new ArrayList<>();

    // 列名前缀
    final String upperColumnPrefix = columnPrefix == null ? null : columnPrefix.toUpperCase(Locale.ENGLISH);
    /**
     * 1、resultMap.getMappedColumns()：获取ResultMap中配置的所有列名
     */
    /* 给"ResultMap中配置的所有列名"拼接上列名前缀 */
    final Set<String> mappedColumns = prependPrefixes(resultMap.getMappedColumns(), upperColumnPrefix);

    /* 遍历ResultSet中的列名集合 */
    for (String columnName : columnNames) {
      // 将列名转为大写，因为之前resultMap中的列名也转换为大写进行存储了
      final String upperColumnName = columnName.toUpperCase(Locale.ENGLISH);

      /* ⚠️如果"ResultMap中配置的列名"包含"ResultSet中的列名"，则添加到mappedColumnNames中，代表配置了映射的列名 */
      if (mappedColumns.contains(upperColumnName)) {
        mappedColumnNames.add(upperColumnName);
      }

      /* ⚠️如果"ResultMap中配置的列名"不包含"ResultSet中的列名"，则添加到unmappedColumnNames中，代表未配置映射的列名 */
      else {
        unmappedColumnNames.add(columnName);
      }
    }

    // ⚠️存放mapKey和"映射的列名集合"的对应关系到mappedColumnNamesMap
    mappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix)/* 获取映射key = resultMap.id:columnPrefix */, mappedColumnNames);

    // ⚠️存放mapKey和"未映射的列名集合"的对应关系到unMappedColumnNamesMap
    unMappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), unmappedColumnNames);
  }

  /**
   * 获取"映射的列名集合"，也就是：ResultMap中配置了映射，且在ResultSet中存在的列名
   *
   * 注意：⚠️如果未获取到"映射的列名集合"，则会加载：1、映射的列名（ResultMap中配置了映射，且在ResultSet中存在的列名）；2、未映射的列名（和ResultMap中未配置映射，但在ResultSet中存在的列名）
   */
  public List<String> getMappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    /* 1、先从缓存中，获取"映射的列名集合"，缓存中有，就直接返回 */
    List<String> mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix)/* 获取映射key = resultMap.id:columnPrefix */);

    /* 2、如果缓存中不存在"映射的列名集合" */
    if (mappedColumnNames == null) {
      /* 2.1、⚠️加载：1、ResultMap中配置了映射，且在ResultSet中存在的列名（映射的列名）；2、和ResultMap中未配置映射，但在ResultSet中存在的列名（未映射的列名） */
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);

      /* 2.2、获取"映射的列名集合"（因为刚刚加载了，所以现在可以获取） */
      mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }

    /* 3、返回"映射的列名集合" */
    return mappedColumnNames;
  }

  /**
   * 获取"未映射的列名集合"，也就是：ResultMap中未配置映射，但在ResultSet中存在的列名。
   *
   * 注意：⚠️如果未获取到"未映射的列名集合"，则会加载：1、映射的列名（ResultMap中配置了映射，且在ResultSet中存在的列名）；2、未映射的列名（和ResultMap中未配置映射，但在ResultSet中存在的列名）
   */
  public List<String> getUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    /* 1、先从缓存中获取"未映射的列名集合"，缓存中有，就直接返回 */
    List<String> unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix)/* 获取映射key = resultMap.id:columnPrefix */);

    /* 2、如果缓存中不存在"未映射的列名集合" */
    if (unMappedColumnNames == null) {

      /* 2.1、⚠️加载：1、ResultMap中配置了映射，且在ResultSet中存在的列名（映射的列名）；2、和ResultMap中未配置映射，但在ResultSet中存在的列名（未映射的列名） */
      loadMappedAndUnmappedColumnNames/* 加载映射和未映射的列名 */(resultMap, columnPrefix);

      /* 2.2、获取"未映射的列名集合"（因为刚刚加载了，所以现在可以获取） */
      unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }

    /* 3、返回"未映射的列名集合" */
    return unMappedColumnNames;
  }

  /**
   * 获取映射key = resultMap.id:columnPrefix
   */
  private String getMapKey(ResultMap resultMap, String columnPrefix) {
    return resultMap.getId() + ":" + columnPrefix;
  }

  /**
   * 给列名拼接上列名前缀
   *
   * @param columnNames 列名集合
   * @param prefix      列名前缀
   * @return
   */
  private Set<String> prependPrefixes(Set<String> columnNames, String prefix) {
    /* 1、列名集合为空，或者不存在列名前缀，就直接返回列名集合 */
    if (columnNames == null || columnNames.isEmpty() || prefix == null || prefix.length() == 0) {
      return columnNames;
    }

    /* 2、否则，将列名集合中，所有的列名都拼接上列名前缀 */
    // 拼接上列名前缀后的列名集合
    final Set<String> prefixed = new HashSet<>();
    // 遍历列名
    for (String columnName : columnNames) {
      // 拼接上列名前缀
      prefixed.add(prefix + columnName);
    }
    // 返回拼接完列名前缀后的列名集合
    return prefixed;
  }

}
