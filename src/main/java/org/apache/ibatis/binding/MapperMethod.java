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
package org.apache.ibatis.binding;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * 映射方法：存放方法相关的一些信息，例如：sql语句，方法参数，返回值类型。在构建MapperMethod的时候，构造器内就已经全部解析并获取到了。
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
public class MapperMethod {

  /**
   * Command：代表的是数据库操作的类别：CURD。后续需要根据命令的类型，来执行不同的sql操作
   */
  // 存放sql语句的唯一标识(name)和sql语句类型(type)
  // 作用：判断下当前的sq语句，指代的是哪种类型的sql操作
  private final SqlCommand command;
  // 当前调用的mapper接口方法的相关信息
  private final MethodSignature/* 方法签名 */ method;

  /**
   * 映射方法对象：
   * 1、创建SqlCommand
   * 2、创建MethodSignature
   *
   * @param mapperInterface
   * @param method
   * @param config
   */
  public MapperMethod/* 映射方法 */(Class<?> mapperInterface, Method method, Configuration config) {
    /*

    1、创建SqlCommand，SqlCommand里面包含了sql语句的唯一标识(name)和sql语句类型(type)

    题外：SqlCommand构造方法干的事情：
    >>> (1)通过"mapper接口全限定名 + 方法名"，组装成一个statementId；
    >>> (2)然后通过statementId，获取当前方法对应的MappedStatement，确立statementId(sql语句的唯一标识)和sql语句类型

     */
    this.command = new SqlCommand(config, mapperInterface, method);

    /*

    2、创建MethodSignature(方法签名对象)，里面就保存了3种数据：
    >>> 1、设置一些返回值相关的标识位，例如：返回值是不是void、是不是返回多条，是不是返回游标类型
    >>> 2、设置关键参数类型（RowBounds、ResultHandler）的索引位置
    >>> 3、创建参数名称解析器(ParamNameResolver)，构造方法里面就干了一件事情：解析得到"参数索引位置"和"参数名称"之间的对应关系，解析了@Param

    */
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;

    /* 根据sql语句类型，选择调用SqlSession对应的方法执行sql */
    /* 注意：执行sql语句时，不进行sql语句的提交/回滚 */

    switch (command.getType()) {

      /* 1、insert */
      case INSERT: {
        // 使用ParamNameResolver处理args数组，将用户传入的实参与指定参数名称关联起来
        Object param = method.convertArgsToSqlCommandParam(args);
        // 调用sqlSession.insert方法，rowCountResult方法会根据method字段中记录的方法的返回值类型对结果进行转换
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      /* 2、update */
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      /* 3、delete */
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      /* 4、select */
      case SELECT:
        /* 4.1、当前方法返回值为void && 当前方法参数上有结果处理器 */
        // 当前方法返回值为void && 当前方法参数上有结果处理器
        // 处理返回值为void，是ResultSet通过ResultHandler处理的方法
        if (method.returnsVoid()/* 当前方法有没有返回值 */ && method.hasResultHandler()/* 当前方法参数上有没有结果处理器 */) {
          // 如果有结果处理器，使用结果处理程序执行

          // 使用ResultHandler执行
          executeWithResultHandler(sqlSession, args);
          result = null;
        }
        /* 4.2、处理返回值为集合和数组的方法 */
        // 处理返回值为集合和数组的方法
        else if (method.returnsMany()) {
          // 如果结果有多条记录
          result = executeForMany(sqlSession, args);
        }
        /* 4.3、处理返回值为map的方法 */
        // 处理返回值为map的方法
        else if (method.returnsMap()) {
          // 如果结果是map
          result = executeForMap(sqlSession, args);
        }
        /* 4.4、处理返回值为游标的方法 */
        // 处理返回值为cursor的方法
        else if (method.returnsCursor()) {
          result = executeForCursor(sqlSession, args);
        }
        /* 4.5、处理返回值为单一对象的方法 */
        // 处理返回值为单一对象的方法
        else {
          /*

          (1)获取"参数名"与"实参(入参对象)"之间的对应关系，方便后面填入sql语句中：
          >>> 1、方法没有参数，或者不存在有效参数，则返回null
          >>> 2、方法只有一个有效参数，且未使用@Param，则直接获取入参对象返回（没有参数名）
          >>> 3、方法有效参数使用了@Param，或者存在多个有效参数，则返回【"参数名称"与"实参(入参对象)"之间的对应关系】

           */
          Object param = method.convertArgsToSqlCommandParam(args);

          /* (2)查询结果 */
          // DefaultSqlSession
          result = sqlSession.selectOne(command.getName()/* statementId */, param);

          /* (3)如果方法返回值类型是Optional类型，并且结果对象不是Optional类型，则给结果对象套上一个Optional，然后返回 */
          // 方法返回值为Optional类型 && (结果为null || 方法返回值类型与结果类型不相等)
          if (method.returnsOptional()
            && (result == null || !method.getReturnType()/* 方法返回值类型 */.equals(result.getClass()/* 结果类型 */))) {
            // 给结果对象套一个Optional，然后返回
            result = Optional.ofNullable(result);
          }
        }
        break;
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }

    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
        + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }

    return result;
  }

  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      result = (long) rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  private void executeWithResultHandler/* 使用ResultHandler执行 */(SqlSession sqlSession, Object[] args) {

    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName()/* sql语句唯一标识 */);

    // 【不是存储过程 && 结果映射的返回值类型是void】则报错
    if (!StatementType.CALLABLE.equals(ms.getStatementType())
      && void.class.equals(ms.getResultMaps().get(0).getType())) {
      // "method" + command.getName() + " 需要@ResultMap 注释、@ResultType 注释、" + " 或 XML 中的 resultType 属性，因此 ResultHandler 可以用作参数。
      throw new BindingException("method " + command.getName()
        + " needs either a @ResultMap annotation, a @ResultType annotation,"
        + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }

    Object param = method.convertArgsToSqlCommandParam/* 将Args转换为Sql命令参数 */(args);
    if (method.hasRowBounds()) {  // 分页
      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName()/* statementId */, param/* sql语句所需参数值 */, method.extractResultHandler(args)/* 获取方法中的ResultHandler参数值 */);
    }
  }

  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectList(command.getName(), param, rowBounds);
    } else {
      // sqlSessionTemplate
      result = sqlSession.selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }

  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectCursor(command.getName(), param);
    }
    return result;
  }

  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    Object collection = config.getObjectFactory().create(method.getReturnType());
    MetaObject metaObject = config.newMetaObject(collection);
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    Object array = Array.newInstance(arrayComponentType, list.size());
    if (arrayComponentType.isPrimitive()) {
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    } else {
      return list.toArray((E[]) array);
    }
  }

  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  // sql命令，静态内部类
  public static class SqlCommand {

    // statementId(sql语句的唯一标识)
    // mapper接口方法唯一标识，由"mapper接口全限定名+方法名"组成，例如：com.msb.mybatis_02.dao.UserDao.getUser
    private final String name;

    // SOL语句类型
    private final SqlCommandType type;

    /**
     * (1)通过"mapper接口全限定名 + 方法名"，组装成一个statementId；
     * (2)然后通过statementId，获取当前方法对应的MappedStatement，确立statementId(sql语句的唯一标识)和sql语句类型
     *
     * @param configuration
     * @param mapperInterface
     * @param method
     */
    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      // 获取方法名称，例如：getUser
      final String methodName = method.getName();
      // 获取方法所在的声明类
      final Class<?> declaringClass = method.getDeclaringClass();

      /* 1、获取当前方法对应的MappedStatement */
      /**
       * 具体流程：
       * (1)先是用"mapper接口全限定名 + 方法名"，组装成一个statementId(sql语句的唯一标识)
       * (2)然后通过statementId，去configuration.mappedStatements中，获取当前方法对应的MappedStatement
       */
      // 从configuration.mappedStatements中，获取当前方法对应的MappedStatement
      // 题外：statementId(sql语句的唯一标识) = mapper接口全限定名 + 方法名
      MappedStatement ms = resolveMappedStatement/* 解析得到一个MappedStatement */(mapperInterface, methodName, declaringClass,
        configuration);

      /* 2、如果MappedStatement为空，则获取方法上的@Flush，如果存在，则name=null、type = SqlCommandType.FLUSH；如果方法上不存在@Flush，则报错 */
      if (ms == null) {
        // 方法上存在@Flush
        if (method.getAnnotation(Flush.class) != null) {
          name = null;
          type = SqlCommandType.FLUSH;
        }
        // 不存在@Flush，则报错
        else {
          throw new BindingException("Invalid bound statement (not found): "/* 无效的绑定语句（未找到） */
            + mapperInterface.getName() + "." + methodName);
        }
      }
      /* 3、存在MappedStatement，则name=MappedStatement.id、type = MappedStatement里面的SqlCommandType */
      // 初始化name和type
      else {
        // name = MappedStatement.id
        // mapper接口方法唯一标识，由"mapper接口全限定名+方法名"组成，例如：com.msb.mybatis_02.dao.UserDao.getUser
        name = ms.getId();
        // SOL语句的类型
        type = ms.getSqlCommandType();

        // 未知的sql语句类型，则抛出异常
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    // statementId = "mapper接口全限定名+方法名"组成
    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    /**
     * 从configuration.mappedStatements中，获取当前方法对应的MappedStatement
     * (1)用【Mapper接口全限定名 + 方法名】组成statementId，也就是【sql语句的唯一标识】
     * (2)从configuration.mappedStatements中，获取statementId所对应的MappedStatement
     * <p>
     * 题外：statementId(sql语句的唯一标识) = Mapper接口全限定名 + 方法名
     *
     * @param mapperInterface mapper接口
     * @param methodName      当前调用的方法
     * @param declaringClass  方法所在的声明类
     * @param configuration   configuration
     * @return
     */
    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
                                                   Class<?> declaringClass, Configuration configuration) {

      /* 1、用【Mapper接口全限定名 + 方法名】组成statementId，也就是【sql语句的唯一标识】 */
      // 用【Mapper接口全限定名 + 方法名】组成statementId，也就是【SQL语句的唯一标识】
      // 例如：com.msb.mybatis_02.dao.UserDao.getUser
      String statementId/* sql语句的唯一标识 */ = mapperInterface.getName()/* 接口的全限定名 */ + "." + methodName;

      /* 2、从configuration.mappedStatements中，获取statementId所对应的MappedStatement */
      // 检查一下配置项当中有没有当前名称的sql语句
      // 如果包含了，就直接从configuration.mappedStatements集合中查找对应的MappedStatement对象，MappedStatement对象中封装了SQL语句
      if (configuration.hasStatement(statementId)) {
        return configuration.getMappedStatement(statementId);
      }

      /* 3、如果configuration.mappedStatements中，不存在statementId所对应的MappedStatement，且当前调用的方法所在的声明类就是当前mapper接口，则返回null */
      // 如果不包含，且方法所在的声明类就是当前mapper接口，则返回null
      else if (mapperInterface.equals(declaringClass)) {
        return null;
      }

      /*

      4、如果configuration.mappedStatements中，不存在statementId所对应的MappedStatement，但是当前调用的方法所在的声明类，不是当前mapper接口，
      则获取当前mapper接口继承的所有接口，然后找到声明当前方法的接口，用【声明当前方法的接口全限定名+方法名】组成新的statementId，
      然后用新的statementId，从configuration.mappedStatements中，获取statementId所对应的MappedStatement返回；
      如果最终没有获取到，则返回null

      */
      for (Class<?> superInterface : mapperInterface.getInterfaces()/* 获取mapper接口继承的所有接口 */) {
        if (declaringClass.isAssignableFrom(superInterface)) {
          MappedStatement ms = resolveMappedStatement(superInterface, methodName,
            declaringClass, configuration);
          if (ms != null) {
            return ms;
          }
        }
      }
      return null;
    }
  }

  // 方法签名，静态内部类
  public static class MethodSignature {

    /* 返回值标识位 */
    // "方法的返回值类型是不是集合类型，或者数组类型"的标识
    private final boolean returnsMany;
    // 返回值类型是否为map类型
    private final boolean returnsMap;
    // 返回值类型是否为void
    private final boolean returnsVoid;
    // 返回值是否为Cursor(游标)类型
    private final boolean returnsCursor;
    // 返回值是否为Optional类型
    private final boolean returnsOptional;
    // 返回值类型
    private final Class<?> returnType;
    // 如果返回值类型是map，则该字段记录了作为key的列名
    private final String mapKey;

    /* 关键参数类型索引位 */
    // 用来标记该方法参数列表中ResultHandler类型参数的位置
    // 结果处理器的索引位置
    // 题外：方法当中没有ResultHandler类型参数，则该值为null
    private final Integer resultHandlerIndex;
    // 用来标记该方法参数列表中RowBounds类型参数的位置
    // 分页处理器的索引位置
    // 题外：方法当中没有RowBounds类型参数，则该值为null
    private final Integer rowBoundsIndex;

    /* 参数名称解析器，里面解析了@Param */
    // 该方法对应的参数名称解析器
    private final ParamNameResolver paramNameResolver;

    /**
     * 方法签名对象，干的3件事：
     * 1、设置一些返回值相关的标识位，例如：返回值是不是void、是不是返回多条，是不是返回游标类型
     * 2、设置关键参数类型（RowBounds、ResultHandler）的索引位置
     * 3、创建参数名称解析器(ParamNameResolver)，构造方法里面就干了一件事情：解析@Param得到"参数索引位置"和"参数名称"之间的对应关系
     *
     * @param configuration
     * @param mapperInterface
     * @param method
     */
    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      /*

      1、设置一些返回值相关的标识位，例如：返回值是不是void、是不是返回多条，是不是返回游标类型

      （题外：提前把返回值标识位设置好，后面好进行相关操作。我在返回对应结果的时候，会根据这些具体的标识来判断，我应该执行哪种处理过程，应该封装什么样的结果返回）

       */

      //（1）获取方法的返回值类型
      // 解析方法的返回值类型
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      // 验证一下是不是Class类型
      if (resolvedReturnType instanceof Class<?>) {
        this.returnType = (Class<?>) resolvedReturnType;
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        this.returnType = method.getReturnType();
      }
      //（2）看一下方法返回值类型是不是void，也就是：是不是没有返回值
      this.returnsVoid = void.class.equals(this.returnType);
      //（3）看一下当前返回值类型是不是一个集合 || 数组类型
      this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      //（4）看一下当前返回值类型是不是一个游标类型
      this.returnsCursor = Cursor.class.equals(this.returnType);
      //（5）看一下当前返回值类型是不是一个Optional类型
      this.returnsOptional = Optional.class.equals(this.returnType);
      //（6）若方法返回值是Map类型，且指定了@MapKey，则获取@MapKey的value属性值作为返回值类型Map的key
      // 注意：⚠️里面获取了方法上的@MapKey
      this.mapKey = getMapKey(method);
      //（7）方法返回值是不是map类型。存在mapKey，就一定是map类型！
      this.returnsMap = this.mapKey != null;

      /* 2、设置关键参数类型（RowBounds、ResultHandler）的索引位置 */
      //（1）看一下方法参数列表当中是否存在RowBounds类型参，如果存在，就获取RowBounds类型参数在方法参数列表中的索引位置
      // 题外：如果方法当中没有RowBounds类型参数，则该值为null
      this.rowBoundsIndex = getUniqueParamIndex/* 获取唯一参数索引 */(method, RowBounds.class);
      //（2）看一下方法参数列表当中是否存在ResultHandler类型参，如果存在，就获取ResultHandler类型参数在方法参数列表中的索引位置
      // 题外：如果方法当中没有ResultHandler类型参数，则该值为null
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);

      /*

      3、创建参数名称解析器(ParamNameResolver)，构造方法里面就干了一件事情：解析得到"参数索引位置"和"参数名称"之间的对应关系
      >>> 1、先获取参数上的@Param，如果有，就从@Param中获取参数名称
      >>> 2、如果参数上没有@Param：
      >>> （1）则看下是否允许使用实际参数名称(默认为true)，如果允许，则使用实际参数名称。一般为arg0，arg1，arg2之类的
      >>> （2）如果不允许使用实际参数名称，则使用参数的索引作为参数名称

      */
      //（1）创建参数名称处理器，里面解析了@Param
      // 我们定义的方法参数名称，在源码编译后，是变为arg0、arg1，之类的参数名称，无法进行匹配。
      // 我们想进行具体参数名称匹配的话（为了让参数名称匹配得上），需要加一个@Param注解来指定参数名称，这样才能完成具体名称的参数解析工作
      // ParamNameResolver就是处理@Param的
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }

    /**
     * 获取"参数名"与"实参"之间的对应关系，方便后面填入sql语句中：
     * 1、方法没有参数，或者不存在有效参数，则返回null
     * 2、方法只有一个有效参数，且未使用@Param，则直接获取入参对象返回（没有参数名）
     * 3、方法有效参数使用了@Param，或者存在多个有效参数，则返回【"参数名称"与"实参(入参对象)"之间的对应关系】
     *
     * @param args 实参数组
     */
    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    // 通过"方法参数列表中RowBounds类型参数的索引位置"，来判断，方法是否存在RowBounds参数
    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    // 通过"方法参数列表中ResultHandler类型参数的位置"，来判断，方法是否存在ResultHandler参数
    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    /**
     * 获取方法中ResultHandler参数值
     */
    public ResultHandler extractResultHandler(Object[] args) {
      return
        // 方法参数中，是否存在ResultHandler参数
        hasResultHandler() ?
          // 存在，则通过"当前方法ResultHandler参数索引位置"获取到传入的ResultHandler参数值
          (ResultHandler) args[resultHandlerIndex] :
          // 不存在，则返回null
          null;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    /**
     * return whether return type is {@code java.util.Optional}.
     *
     * @return return {@code true}, if return type is {@code java.util.Optional}
     * @since 3.5.0
     */
    public boolean returnsOptional() {
      return returnsOptional;
    }

    private Integer getUniqueParamIndex/* 获取唯一参数索引 */(Method method, Class<?> paramType) {
      Integer index = null;
      // 获取方法参数类型
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        // 判断方法参数类型，是不是paramType类型
        if (paramType.isAssignableFrom(argTypes[i])) {
          if (index == null) {
            // 记录paramType类型参数，在方法参数列表中的索引位置
            index = i;
          } else {
            // RowBounds和ResultHandler类型的参数只能有一个，不能重复出现
            throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
        }
      }
      return index;
    }

    public String getMapKey() {
      return mapKey;
    }

    /**
     * 先判断方法返回值是不是Map类型，如果是，再获取方法上的@MapKey；
     * 如果方法上存在@MapKey，则将@MapKey中的value，作为返回类型Map的key
     */
    private String getMapKey(Method method) {
      String mapKey = null;
      // 判断方法返回值类型是不是Map类型
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        // 如果方法返回值类型是Map类型，则获取方法上的@MapKey
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        // 如果方法上存在@MapKey，则将@MapKey的value值作为map的key
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }
  }

}
