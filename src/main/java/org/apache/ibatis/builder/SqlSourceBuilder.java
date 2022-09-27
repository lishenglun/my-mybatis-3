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
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 */
public class SqlSourceBuilder extends BaseBuilder {

  private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }

  /**
   * 解析sql
   * （1）解析sql，得到jdbc可执行的sql；
   * （2）同时为每一个sql参数，构建sql参数映射（ParameterMapping，面保存了sql参数名和参数类型）
   *
   * @param originalSql
   * @param parameterType
   * @param additionalParameters
   * @return
   */
  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
    // 作用：构建sql参数映射
    // 题外："sql参数映射"保存在里面
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
    // 作用：解析sql，得到jdbc可执行sql
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);


    /*

    解析sql

    （1）解析sql，得到jdbc可执行的sql；

    例如：text = select * from user WHERE id = #{id} and username = #{username}
    最终，builder = select * from user WHERE id = ? and username = ?

    （2）同时为每一个sql参数，构建sql参数映射（ParameterMapping：面保存了sql参数名和参数类型）

    例如：#{id}中的id，会构建id对应的ParameterMapping对象，并且获取id这个属性名称在参数对象中的属性类型，比如，User中id属性类型为Integer，
    那么就会获取到id在参数对象User中的属性类型为Integer，然后创建ParameterMapping对象，ParameterMapping对象里面保存了id和id属性类型

     */

    String sql;
    // 判断"是否需要在Sql中缩小空格"，为true，则会删除多余的空格
    if (configuration.isShrinkWhitespacesInSql/* 是在Sql中缩小空格 */()) {
      // ⚠️解析得到sql语句
      sql = parser.parse(removeExtraWhitespaces/* 删除多余的空格 */(originalSql));
    } else {
      // ⚠️解析得到sql语句
      sql = parser.parse(originalSql);
    }

    return new StaticSqlSource(configuration, sql/* sql */, handler.getParameterMappings()/* sql参数映射 */);
  }

  public static String removeExtraWhitespaces(String original) {
    StringTokenizer tokenizer = new StringTokenizer(original);
    StringBuilder builder = new StringBuilder();
    boolean hasMoreTokens = tokenizer.hasMoreTokens();
    while (hasMoreTokens) {
      builder.append(tokenizer.nextToken());
      hasMoreTokens = tokenizer.hasMoreTokens();
      if (hasMoreTokens) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {

    // ⚠️sql参数映射（里面存放了表达式和java类型之间的关系）
    private final List<ParameterMapping> parameterMappings = new ArrayList<>();

    private final Class<?> parameterType; // 参数类型，例如：getUserByUser2(User user)中的User
    private final MetaObject metaParameters;

    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }

    /**
     * 构建表达式和参数映射的关系
     */
    @Override
    public String handleToken(String content) {
      // ⚠️构建sql参数映射：去判断参数对象中，是否存在对应的属性；如果存在，就从参数对象中获取属性的类型，然后构建它两的映射关系
      // 例如：content = id，它是User中的属性，那么就会去User中获取到id属性的类型，然后建设一个ParameterMapping对象，将他俩的关系保存起来
      ParameterMapping parameterMapping = buildParameterMapping/* 构建参数映射 */(content);
      parameterMappings.add(parameterMapping);
      return "?";
    }

    /**
     * 构建sql参数映射：去判断参数对象中，是否存在对应的属性；如果存在，就从参数对象中获取属性的类型，然后构建它两的映射关系
     */
    private ParameterMapping buildParameterMapping/* 构建参数映射 */(String content) {
      /* 1、修整表达式，例如去掉空格，然后存放修整后的表达式。key为property，value为修整后的表达式。 */
      Map<String, String> propertiesMap = parseParameterMapping(content);

      /* 2、获取表达式对应的java类型 */

      // 获取修整后的表达式
      String property = propertiesMap.get("property");
      // 表达式类型
      Class<?> propertyType;
      if (metaParameters.hasGetter(property)) { // issue #448 get type from additional params
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        propertyType = java.sql.ResultSet.class;
      } else if (property == null || Map.class.isAssignableFrom(parameterType)) {
        propertyType = Object.class;
      } else {
        // ⚠️获取参数类型对应的MetaClass
        MetaClass metaClass = MetaClass.forClass(parameterType/* 参数类型 */, configuration.getReflectorFactory()/* 反射工厂 */);

        /**
         * 例如：
         * parameterType = User
         * property = id
         *
         * 那就判断User当中是否存在getId()方法
         */
        // 判断参数对象中是否存在这个表达式名称的get方法
        if (metaClass.hasGetter(property)) {
          // 如果存在，那么就获取表达式对应的java类型
          propertyType = metaClass.getGetterType(property);   // 获取属性类型
        } else {
          propertyType = Object.class;
        }
      }

      /* 这里都不成立 */

      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);

      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if ("javaType".equals(name)) {
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          builder.jdbcType(resolveJdbcType(value));
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + PARAMETER_PROPERTIES);
        }
      }

      if (typeHandlerAlias != null) {
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }

      // 注意：⚠️里面解析获取当前参数类型对应的TypeHandler
      return builder.build();
    }

    private Map<String, String> parseParameterMapping(String content) {
      try {
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }

}
