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
import java.util.Set;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
public class ResultMapping {

  private Configuration configuration;
  // 属性名
  private String property;
  // 列名
  private String column;
  // 属性类型
  private Class<?> javaType;
  // 列类型
  private JdbcType jdbcType;
  private TypeHandler<?> typeHandler;
  /**
   * 1、要么是标签中的resultMap属性值，作为nestedResultMapId
   *
   * —— 简单概括：resultMap属性值
   *
   * 2、要么是标签中没有配置resultMap属性值，则将<association>、<collection>、<case>作为嵌套的resultMap，
   * 会当作<resultMap>标签进行解析，构建对应的对应的ResultMap，返回由这3个标签构建好的ResultMap.id，作为nestedResultMapId
   *
   * —— 简单概括：没resultMap属性值，则用<association>、<collection>、<case>标签对应的ResultMap.id
   *
   * 题外：因为这3个标签可以配置<resultMap>标签下的所有子标签，所以将这3个标签当作<resultMap>标签进行解析，构建对应的对应的ResultMap；
   * 因为这3个标签是作为<resultMap>标签中的<resultMap>标签进行处理，所以也是作为嵌套的resultMap，返回ResultMap.id；
   * 同时因为这3个标签是作为嵌套的resultMap，所以即使这3个标签没有配置resultMap属性，其对应的ResultMapping中会有对应的nestedResultMapId
   */
  // 嵌套的resultMap.id
  private String nestedResultMapId;
  /**
   * 例如：其中的select属性：com.msb.mybatis_02.dao.RoleDao.getRoleByUserId，就是nestedQueryId
   * <collection select="com.msb.mybatis_02.dao.RoleDao.getRoleByUserId"/>
   */
  // 查询id，
  private String nestedQueryId;
  private Set<String> notNullColumns;
  // 列名前缀
  private String columnPrefix;
  private List<ResultFlag> flags;
  // 复合列名
  // 例如：column = {prop1=col1,prop2=col2}，一般与嵌套查询配合使用，表示将col1和col2的列值传递给内层嵌套，
  // >>> 将会得到：
  // >>> property是prop1，column是col1，这个键值对构成ResultMapping；
  // >>> property是prop2，column是col2，这个键值对构成ResultMapping；
  private List<ResultMapping> composites;
  /**
   * 只有<collection>、<association>标签中存在resultSet属性
   */
  private String resultSet;
  private String foreignColumn;
  private boolean lazy;

  ResultMapping() {
  }

  public static class Builder {

    private ResultMapping resultMapping = new ResultMapping();

    public Builder(Configuration configuration, String property, String column, TypeHandler<?> typeHandler) {
      this(configuration, property);
      resultMapping.column = column;
      resultMapping.typeHandler = typeHandler;
    }

    public Builder(Configuration configuration, String property, String column, Class<?> javaType) {
      this(configuration, property);
      resultMapping.column = column;
      resultMapping.javaType = javaType;
    }

    public Builder(Configuration configuration, String property) {
      resultMapping.configuration = configuration;
      resultMapping.property = property;
      resultMapping.flags = new ArrayList<>();
      resultMapping.composites = new ArrayList<>();
      resultMapping.lazy = configuration.isLazyLoadingEnabled();
    }

    public Builder javaType(Class<?> javaType) {
      resultMapping.javaType = javaType;
      return this;
    }

    public Builder jdbcType(JdbcType jdbcType) {
      resultMapping.jdbcType = jdbcType;
      return this;
    }

    public Builder nestedResultMapId(String nestedResultMapId) {
      resultMapping.nestedResultMapId = nestedResultMapId;
      return this;
    }

    public Builder nestedQueryId(String nestedQueryId) {
      resultMapping.nestedQueryId = nestedQueryId;
      return this;
    }

    public Builder resultSet(String resultSet) {
      resultMapping.resultSet = resultSet;
      return this;
    }

    public Builder foreignColumn(String foreignColumn) {
      resultMapping.foreignColumn = foreignColumn;
      return this;
    }

    public Builder notNullColumns(Set<String> notNullColumns) {
      resultMapping.notNullColumns = notNullColumns;
      return this;
    }

    public Builder columnPrefix(String columnPrefix) {
      resultMapping.columnPrefix = columnPrefix;
      return this;
    }

    public Builder flags(List<ResultFlag> flags) {
      resultMapping.flags = flags;
      return this;
    }

    public Builder typeHandler(TypeHandler<?> typeHandler) {
      resultMapping.typeHandler = typeHandler;
      return this;
    }

    public Builder composites(List<ResultMapping> composites) {
      resultMapping.composites = composites;
      return this;
    }

    public Builder lazy(boolean lazy) {
      resultMapping.lazy = lazy;
      return this;
    }

    public ResultMapping build() {
      // lock down collections —— 锁定收藏
      resultMapping.flags = Collections.unmodifiableList(resultMapping.flags);
      resultMapping.composites = Collections.unmodifiableList(resultMapping.composites);

      // ⚠️根据java类型和jdbc类型，获取TypeHandler：
      // 1、先根据java类型，从"java类型处理器集合(typeHandlerMap)"中，获取到对应的"jdbc类型处理器集合"；
      // 2、如果能获取到"jdbc类型处理器集合"；则根据jdbc类型，从"jdbc类型处理器集合"中，获取到对应的TypeHandler；
      // 3、如果没有获取到"java类型"对应的"jdbc类型处理器集合"，则返回null
      resolveTypeHandler();

      // 验证resultMap是否配置正确
      validate();

      return resultMapping;
    }

    /**
     * 验证resultMap是否配置正确
     */
    private void validate() {
      /*

      1、不能同时配置select属性和resultMap属性！
      例如：<collection select="com.msb.mybatis_02.dao.RoleDao.getRoleByUserId" resultMap="roleMap"/>

       */
      // Issue #697: cannot define both nestedQueryId and nestedResultMapId
      if (resultMapping.nestedQueryId != null && resultMapping.nestedResultMapId != null) {
        // 不能在属性中同时定义 nestedQueryId 和 nestedResultMapId
        throw new IllegalStateException("Cannot define both nestedQueryId and nestedResultMapId in property " + resultMapping.property);
      }
      /*

      2、既没有配置select属性，又不是以结果映射的形式，而且不存在当前属性的TypeHandler，则报错

      例如：User里面有个属性，叫role，是Role对象。mybatis里面是没有Role对应的TypeHandler，来直接获取到Role类型的数据，填充这个属性值的！
      所以直接配置<result column="role" property="role"/>，会报错，

      <resultMap id="userMap" type="com.msb.mybatis_02.bean.User">
        <id column="id" property="id"/>
        <result column="role" property="role"/>
      </resultMap>

      但是如果配置为如下，把role属性当作一个结果映射，就不会报错！因为有对应的TypeHandler去处理，逐一处理<association>里面的结果映射(例如有IntegerTypeHandler，处理<association>里面的userId)，
      最终再把<association>里面的每个属性值的结果，汇总成为一个Role，才可以放入到role属性中！

      <resultMap id="userMap" type="com.msb.mybatis_02.bean.User">
        <id column="id" property="id"/>
        <association property="role" javaType="com.msb.mybatis_02.bean.Role">
          <id column="id" property="id"/>
          <result column="user_id" property="userId"/>
          <result column="name" property="name"/>
          <result column="position" property="position"/>
        </association>
      </resultMap>

      */
      // Issue #5: there should be no mappings without typehandler —— 没有类型处理程序就不应该有映射
      if (resultMapping.nestedQueryId/* 嵌套查询ID */ == null && resultMapping.nestedResultMapId/* 嵌套的结果映射id */ == null && resultMapping.typeHandler == null) {
        // 找不到属性的类型处理程序
        throw new IllegalStateException("No typehandler found for property " + resultMapping.property);
      }

      // Issue #4 and GH #39: column is optional only in nested resultmaps but not in the rest
      if (resultMapping.nestedResultMapId == null && resultMapping.column == null && resultMapping.composites.isEmpty()) {
        throw new IllegalStateException("Mapping is missing column attribute for property " + resultMapping.property);
      }

      /* 存在resultSet属性值，则比较column和foreignColumn中配置的列数，如果不相等，则报错！（2个都不存在，算是相等，不会报错） */

      // 题外：只有<collection>、<association>标签中存在resultSet属性
      if (resultMapping.getResultSet() != null) {
        // 属性中的列数
        int numColumns = 0;
        if (resultMapping.column != null) {
          numColumns = resultMapping.column.split(",").length;
        }

        // 外部列数
        int numForeignColumns = 0;
        if (resultMapping.foreignColumn != null) {
          numForeignColumns = resultMapping.foreignColumn.split(",").length;
        }

        // 如果属性中的列数和外部列数不相等，则报错
        if (numColumns != numForeignColumns) {
          throw new IllegalStateException("There should be the same number of columns and foreignColumns in property "/* 属性中的列数和外部列数应该相同 */ + resultMapping.property);
        }
      }
    }

    /**
     * 根据java类型和jdbc类型，获取TypeHandler：
     * 1、先根据java类型，从"java类型处理器集合(typeHandlerMap)"中，获取到对应的"jdbc类型处理器集合"；
     * 2、如果能获取到"jdbc类型处理器集合"；则根据jdbc类型，从"jdbc类型处理器集合"中，获取到对应的TypeHandler；
     * 3、如果没有获取到"java类型"对应的"jdbc类型处理器集合"，则返回null
     */
    private void resolveTypeHandler() {
      if (resultMapping.typeHandler == null && resultMapping.javaType != null) {
        Configuration configuration = resultMapping.configuration;
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        // ⚠️根据java类型和jdbc类型，获取TypeHandler：
        // 1、先根据java类型，从"java类型处理器集合(typeHandlerMap)"中，获取到对应的"jdbc类型处理器集合"；
        // 2、如果能获取到"jdbc类型处理器集合"；则根据jdbc类型，从"jdbc类型处理器集合"中，获取到对应的TypeHandler；
        // 3、如果没有获取到"java类型"对应的"jdbc类型处理器集合"，则返回null
        resultMapping.typeHandler = typeHandlerRegistry.getTypeHandler(resultMapping.javaType/* Java类型 */, resultMapping.jdbcType/* jdbc类型(一般为null) */);
      }
    }

    public Builder column(String column) {
      resultMapping.column = column;
      return this;
    }
  }

  public String getProperty() {
    return property;
  }

  public String getColumn() {
    return column;
  }

  public Class<?> getJavaType() {
    return javaType;
  }

  public JdbcType getJdbcType() {
    return jdbcType;
  }

  public TypeHandler<?> getTypeHandler() {
    return typeHandler;
  }

  public String getNestedResultMapId() {
    return nestedResultMapId;
  }

  public String getNestedQueryId() {
    return nestedQueryId;
  }

  public Set<String> getNotNullColumns() {
    return notNullColumns;
  }

  public String getColumnPrefix() {
    return columnPrefix;
  }

  public List<ResultFlag> getFlags() {
    return flags;
  }

  public List<ResultMapping> getComposites() {
    return composites;
  }

  public boolean isCompositeResult() {
    return this.composites != null && !this.composites.isEmpty();
  }

  public String getResultSet() {
    return this.resultSet;
  }

  public String getForeignColumn() {
    return foreignColumn;
  }

  public void setForeignColumn(String foreignColumn) {
    this.foreignColumn = foreignColumn;
  }

  public boolean isLazy() {
    return lazy;
  }

  public void setLazy(boolean lazy) {
    this.lazy = lazy;
  }

  public boolean isSimple() {
    return this.nestedResultMapId == null && this.nestedQueryId == null && this.resultSet == null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ResultMapping that = (ResultMapping) o;

    return property != null && property.equals(that.property);
  }

  @Override
  public int hashCode() {
    if (property != null) {
      return property.hashCode();
    } else if (column != null) {
      return column.hashCode();
    } else {
      return 0;
    }
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("ResultMapping{");
    //sb.append("configuration=").append(configuration); // configuration doesn't have a useful .toString()
    sb.append("property='").append(property).append('\'');
    sb.append(", column='").append(column).append('\'');
    sb.append(", javaType=").append(javaType);
    sb.append(", jdbcType=").append(jdbcType);
    //sb.append(", typeHandler=").append(typeHandler); // typeHandler also doesn't have a useful .toString()
    sb.append(", nestedResultMapId='").append(nestedResultMapId).append('\'');
    sb.append(", nestedQueryId='").append(nestedQueryId).append('\'');
    sb.append(", notNullColumns=").append(notNullColumns);
    sb.append(", columnPrefix='").append(columnPrefix).append('\'');
    sb.append(", flags=").append(flags);
    sb.append(", composites=").append(composites);
    sb.append(", resultSet='").append(resultSet).append('\'');
    sb.append(", foreignColumn='").append(foreignColumn).append('\'');
    sb.append(", lazy=").append(lazy);
    sb.append('}');
    return sb.toString();
  }

}
