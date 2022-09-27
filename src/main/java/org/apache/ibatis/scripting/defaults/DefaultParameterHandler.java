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
package org.apache.ibatis.scripting.defaults;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class DefaultParameterHandler implements ParameterHandler {

  private final TypeHandlerRegistry typeHandlerRegistry;

  private final MappedStatement mappedStatement;
  private final Object parameterObject;
  private final BoundSql boundSql;
  private final Configuration configuration;

  public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    this.mappedStatement = mappedStatement;
    this.configuration = mappedStatement.getConfiguration();
    this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
    this.parameterObject = parameterObject;
    this.boundSql = boundSql;
  }

  @Override
  public Object getParameterObject() {
    return parameterObject;
  }

  /**
   * 设置sql参数：
   * （1）根据，之前getBoundSql()中构建的参数映射（ParameterMapping）中获取参数名、实参数对象；然后根据参数名去实参对象中，获取参数值；
   * （2）然后通过TypeHandler，向指定索引位置，设置对应类型的参数值。TypeHandler最终也是调用Statement，通过Statement，设置sql参数值。
   *
   * @param ps
   */
  @Override
  public void setParameters(PreparedStatement ps) {
    // 记录日志信息的
    ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());

    // 取出sql中的参数映射列表
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

    /* 1、遍历sql参数映射列表 */

    // 注意：⚠️只有当parameterMappings不为空时，才证明sql语句中需要有设置的参数
    if (parameterMappings != null) {
      for (int i = 0; i < parameterMappings.size(); i++) {
        // 单个参数映射
        ParameterMapping parameterMapping = parameterMappings.get(i);

        // 如果不是存储过程中的输出参数（过滤掉存储过程中的输出参数）
        if (parameterMapping.getMode() != ParameterMode.OUT) {

          // 实参
          Object value;
          /* 参数名称 */
          String propertyName = parameterMapping.getProperty();

          /* 2、获取实参 */

          // 获取对应的实参值
          // 判断有没有对应的参数值
          if (boundSql.hasAdditionalParameter/* 有附加参数 */(propertyName)) { // issue #448 ask first for additional params —— 问题448首先要求额外的参数
            // 若有额外的参数，设为额外的参数
            value = boundSql.getAdditionalParameter(propertyName);
          }
          // 如果实参对象为null，则sql参数值也为null
          else if (parameterObject == null) {
            value = null;
          }
          // 类型处理注册器里面有没有对应类型的类型处理器
          else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            // 实参可以直接通过TypeHandler转换成JdbcType
            value = parameterObject;
          }
          // ⚠️从实参对象中获取相应名称的属性值作为sql参数值
          // 题外：实参数对象有可能是我们传入的对象，也有可能是一个Map，如果是一个Map，就是从Map中查找值。
          else {
            // ⚠️获取实参对象的MetaObject
            MetaObject metaObject = configuration.newMetaObject(parameterObject/* 参数对象 */);
            // ⚠️从实参对象中获取参数值
            // 注意：前面在构建ParameterMapping的时候，并没有获取到对应的参数值，只是获取了参数名称和按顺序添加到list中，确定了在sql参数中的索引位置！
            value = metaObject.getValue(propertyName/* 参数名称 */);
          }

          /*

          3、通过TypeHandler，向指定索引位置，设置对应类型的参数值。TypeHandler最终也是调用Statement，通过Statement，设置sql参数值。

          */

          // 获取参数映射中设置的TypeHandler（类型处理器），然后通过TypeHandler，向指定索引位置，设置对应类型的参数值
          TypeHandler typeHandler = parameterMapping.getTypeHandler();
          // 获取jdbc类型
          JdbcType jdbcType = parameterMapping.getJdbcType();
          if (value == null && jdbcType == null) {
            //不同类型的set方法不同，所以委派给子类的setParameter方法
            jdbcType = configuration.getJdbcTypeForNull();
          }
          try {
            // 通过typeHandler.setParameter()完成类型转换，并且完成属性的设置工作

            // 调用PreparedStatement.set*方法为SQL语句绑定相应的实参
            // 例如：preparedStatement.setString(1, "王五")，这里只是把preparedStatement换成了typeHandler，通过我们的typeHandler来设置具体的参数值而已
            // >>> 所以当这行代码执行完成之后，我的"?占位符"就没有了；占位符没有了，获取到的就是一条完整的sql语句。
            // 注意：ps = PreparedStatement
            typeHandler.setParameter(ps, i + 1, value, jdbcType);
          } catch (TypeException | SQLException e) {
            throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
          }
        }

      }
    }
  }

}
