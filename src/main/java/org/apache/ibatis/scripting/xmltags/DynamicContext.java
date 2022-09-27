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
package org.apache.ibatis.scripting.xmltags;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import ognl.OgnlContext;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class DynamicContext {

  // 参数对象
  public static final String PARAMETER_OBJECT_KEY = "_parameter";
  // 数据库厂商
  public static final String DATABASE_ID_KEY = "_databaseId";

  static {
    OgnlRuntime.setPropertyAccessor(ContextMap.class, new ContextAccessor());
  }

  private final ContextMap bindings;
  private final StringJoiner sqlBuilder = new StringJoiner(" ");
  private int uniqueNumber = 0;

  /**
   * 构建ContextMap；然后往ContextMap里面设置参数（参数对象、数据库厂商）
   * >>> ContextMap主要包含2个变量：参数对象的MetaObject、是否存在当前参数对象的TypeHandler
   *
   * @param configuration
   * @param parameterObject     参数对象 —— "参数名"与"实参"之间的对应关系
   */
  public DynamicContext(Configuration configuration, Object parameterObject) {
    /* 1、构建ContextMap */

    // （1）参数对象不为null && 参数对象不是Map类型
    if (parameterObject != null && !(parameterObject instanceof Map)) {
      // 构建参数对象的MetaObject
      MetaObject metaObject = configuration.newMetaObject(parameterObject);
      // 是否存在当前参数对象的TypeHandler
      boolean existsTypeHandler = configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass());
      // ⚠️构建ContextMap
      bindings = new ContextMap(metaObject, existsTypeHandler);
    }
    // （2）参数对象为null || 参数对象是Map类型
    else {
      // ⚠️构建ContextMap
      bindings = new ContextMap(null, false);
    }

    /* 2、往ContextMap里面设置参数 */

    // 设置参数对象
    bindings.put(PARAMETER_OBJECT_KEY/* _parameter */, parameterObject);
    // 设置数据库厂商
    bindings.put(DATABASE_ID_KEY/* _databaseId */, configuration.getDatabaseId());
  }

  public Map<String, Object> getBindings() {
    return bindings;
  }

  public void bind(String name, Object value) {
    bindings.put(name, value);
  }

  public void appendSql(String sql) {
    sqlBuilder.add(sql);
  }

  public String getSql() {
    return sqlBuilder.toString().trim();
  }

  public int getUniqueNumber() {
    return uniqueNumber++;
  }

  static class ContextMap extends HashMap<String, Object> {

    private static final long serialVersionUID = 2977601501966151582L;
    // 参数对象的MetaObject
    private final MetaObject parameterMetaObject;
    // 是否存在当前参数对象的TypeHandler
    private final boolean fallbackParameterObject;

    public ContextMap(MetaObject parameterMetaObject, boolean fallbackParameterObject) {
      // 参数对象的MetaObject
      this.parameterMetaObject = parameterMetaObject;
      // 是否存在当前参数对象的TypeHandler
      this.fallbackParameterObject = fallbackParameterObject;
    }

    @Override
    public Object get(Object key) {
      String strKey = (String) key;
      if (super.containsKey(strKey)) {
        return super.get(strKey);
      }

      if (parameterMetaObject == null) {
        return null;
      }

      if (fallbackParameterObject && !parameterMetaObject.hasGetter(strKey)) {
        return parameterMetaObject.getOriginalObject();
      } else {
        // issue #61 do not modify the context when reading
        return parameterMetaObject.getValue(strKey);
      }
    }
  }

  static class ContextAccessor implements PropertyAccessor {

    @Override
    public Object getProperty(Map context, Object target, Object name) {
      Map map = (Map) target;

      Object result = map.get(name);
      if (map.containsKey(name) || result != null) {
        return result;
      }

      Object parameterObject = map.get(PARAMETER_OBJECT_KEY);
      if (parameterObject instanceof Map) {
        return ((Map)parameterObject).get(name);
      }

      return null;
    }

    @Override
    public void setProperty(Map context, Object target, Object name, Object value) {
      Map<Object, Object> map = (Map<Object, Object>) target;
      map.put(name, value);
    }

    @Override
    public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }

    @Override
    public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }
  }
}
