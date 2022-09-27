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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * XML映射构建器（XML mapper构建器）。建造者模式，继承BaseBuilder。
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLMapperBuilder extends BaseBuilder {

  // 里面包含了dao.xml文件的document对象
  private final XPathParser parser;
  /**
   * 注意：⚠️这个是专属于当前XMLMapperBuilder的MapperBuilderAssistant，与MapperAnnotationBuilder中的MapperBuilderAssistant是不同的
   */
  // 映射器构建助手（mapper构建助手）
  private final MapperBuilderAssistant builderAssistant;
  /**
   * 用于抽取重复的sql代码
   * 参考：
   * <sql id="defaultSql">
   *    select * from user
   * </sql>
   */
  // 存放sql片段的集合
  // 注意：此时还没有解析sql片段，直接将XNode放进去了
  private final Map<String, XNode> sqlFragments/* sql片段 */;
  // mapper接口对应的dao.xml文件路径
  // 例如：mapper接口为com.msb.mybatis_02.dao.UserDao，对应的dao.xml文件路径为/com/msb/mybatis_02/dao/UserDao.xml
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
      configuration, resource, sqlFragments);
  }

  /**
   * @param inputStream   加载"mapper接口对应的dao.xml文件"的输入流
   * @param configuration configuration
   * @param resource      mapper接口对应的dao.xml文件路径
   * @param sqlFragments
   * @param namespace     mapper接口全限定名
   */
  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    // 里面构建了一个XPathParser对象，将dao.xml文件解析为一个document对象
    this(inputStream, configuration, resource, sqlFragments);
    // 在未解析dao.xml之前，设置当前命名空间为当前"mapper接口全限定名"
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  /**
   * @param inputStream   加载"mapper接口对应的dao.xml文件"的输入流
   * @param configuration configuration
   * @param resource      mapper接口对应的dao.xml文件路径
   * @param sqlFragments
   */
  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    /* 1、new XPathParser()：会解析dao.xml文件为一个document对象 */
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
      configuration, resource, sqlFragments);
  }

  /**
   * @param parser        XPathParser：里面解析dao.xml文件为一个document对象
   * @param configuration configuration
   * @param resource      mapper接口对应的dao.xml文件路径
   * @param sqlFragments
   */
  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    // ⚠️创建属于当前XMLMapperBuilder的MapperBuilderAssistant
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    // XPathParser：里面解析dao.xml文件为一个document对象
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    // mapper接口对应的dao.xml文件路径
    this.resource = resource;
  }

  /**
   * 解析dao.xml
   */
  public void parse() {
    // 判断是否己经加载过该映射文件（dao.xml）
    // 如果没有加载过再加载，防止重复加载
    if (!configuration.isResourceLoaded(resource)) {
      /* 未加载 */

      /* 1、解析dao.xml */
      /**
       * 1、parser.evalNode("/mapper")：获取dao.xml的根标签 —— dao.xml根标签就是<mapper>。
       * 参考：
       *  <mapper namespace="com.msb.mybatis_02.dao.UserDao">
       *     <select id="getUser" resultType="com.msb.mybatis_02.bean.User">
       *        select * from user;
       *     </select>
       * </mapper>
       */
      // ⚠️从根节点开始，解析dao.xml —— 获取dao.xml文件的根标签(<mapper>标签)，从根标签开始解析dao.xml文件
      configurationElement(parser.evalNode("/mapper"));

      /* 2、将当前解析的dao.xml文件路径保存在configuration中，代表已经加载过了 */
      configuration.addLoadedResource(resource);

      /*

      3、（可忽略）判断一下，是否已经注册了当前命名空间所对应的mapper(configuration.mapperRegistry.knownMappers中是否存在当前命名空间所对应的mapper接口)
      如果被加载过了，则不做任何事情；
      如果未被加载，就注册对应的mapper

      ⚠️可忽略的原因：由于前面已经注册了当前命名空间所对应的mapper，所以这里一般啥事都不做

      */
      // 绑定映射器到namespace
      bindMapperForNamespace();
    }

    /* 4、重新处理解析失败的resultMap节点 */
    // 处理ConfigurationElement方法中解析失败的resultMap节点
    // ⚠️configuration.incompleteResultMaps
    parsePendingResultMaps/* 解析待处理的结果映射 */();

    /* 5、重新处理解析失败的cache-ref节点 */
    // 处理ConfigurationElement方法中解析失败的cache-ref节点
    // ⚠️configuration.incompleteCacheRefs
    parsePendingCacheRefs/* 解析待处理的缓存引用 */();

    /* 6、重新处理解析失败的sql标签（<select><insert><update><delete>） */
    // 处理ConfigurationElement方法中解析失败的SQL语句节点
    // ⚠️configuration.incompleteStatements
    parsePendingStatements/* 解析待处理的语句 */();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }


  /**
   * 解析dao.xml文件
   *
   * 参考：
   * <mapper namespace="com.msb.mybatis_02.dao.UserDao">
   *
   *   <select id="getUserById" parameterType="int" resultType="com.msb.mybatis_02.bean.User">
   *     select *
   *     from user
   *     where id = #{id}
   *   </select>
   *
   * </mapper>
   *
   * 题外：mapper配置文件与dao.xml配置文件是同一个意思，dao.xml配置文件可以称呼为mapper配置文件。
   * >>> 当然mapper配置文件表示的范围更广，因为mapper配置文件，还可能只是纯mapper接口。
   *
   * @param context dao.xml文件的根标签，<mapper>标签，从"dao.xml文件的根标签"开始，解析dao.xml文件
   */
  private void configurationElement(XNode context) {
    try {
      /*

      1、获取dao.xml文件中配置的命名空间，与当前mapper接口的全限定名做比较，如果不一样，则报错；如果一样则设置为当前命名空间

      注意：⚠️由于之前已经将mapper接口的全限定名，设置为了命名空间；所以这里再次设置dao.xml文件中配置的命名空间，会将2者的命名空间做比较，
      >>> 如果不相同，则会报错，也就是说，dao.xml中配置的命名空间，必须是当前mapper接口的全限定名！

      */
      // 获取dao.xml文件中配置的命名空间
      String namespace = context.getStringAttribute("namespace");
      // 如果不存在命名空间，则报错。必须要配置命名空间。
      if (namespace == null || namespace.isEmpty()) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      /**
       * 1、注意：⚠️由于之前已经将mapper接口的全限定名，设置为了命名空间；所以这里再次设置dao.xml文件中配置的命名空间，会将2者的命名空间做比较，
       * >>> 如果不相同，则会报错，也就是说，dao.xml中配置的命名空间，必须是当前mapper接口的全限定名！
       */
      // 设置当前命名空间为dao.xml文件中指定的命名空间
      builderAssistant.setCurrentNamespace(namespace);

      /*

      2、解析<cache-ref>标签
      >>> 也就是：解析缓存引用：从configuration.caches中，通过引用缓存的命名空间，去获取对应的命名空间的缓存，作为引用缓存
      题外：可以使用<cache-ref>标签来引用另外一个命名空间的缓存

      */
      cacheRefElement(context.evalNode("cache-ref"));

      /*

      3、解析<cache>标签
      >>> 也就是：用<cache>标签信息，构建出一个Cache对象（二级缓存，mapper级别），然后放入configuration.caches集合中。当前缓存的命名空间，就是当前dao.xml文件的命名空间。

       */
      cacheElement(context.evalNode("cache"));

      /* 4、配置parameterMap(已经废弃，可以忽略，老式风格的参数映射) */
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));

      /*

      5、⚠️解析所有的<resultMap>标签（高级功能）
      每个<resultMap>标签信息，构建成一个ResultMap，然后放入到CConfiguration.resultMaps中(映射结果集合)，
      >>> key是"命名空间+<resultMap>标签id属性"，
      >>> value就是<resultMap>标签信息构建成的一个ResultMap

      */
      resultMapElements(context.evalNodes("/mapper/resultMap"));

      /* 6、添加sql片段到"sql片段集合(sqlFragments)"中 */
      // 解析<sql>标签
      // >>> 也就是：添加sql片段到"sql片段集合(sqlFragments)"中
      sqlElement(context.evalNodes("/mapper/sql"));

      /* 7、⚠️解析所有的<select><insert><update><delete>标签，得到一个MappedStatement对象，然后放入configuration的映射语句集合中（mappedStatements）*/
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));

    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

  /**
   * 解析所有的<select><insert><update><delete>标签，得到一个MappedStatement对象，然后放入configuration的映射语句集合中（mappedStatements）
   *
   * @param list
   */
  private void buildStatementFromContext(List<XNode> list) {
    // 调用7.1构建语句
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  /**
   * 解析所有的<select><insert><update><delete>标签，得到一个MappedStatement对象，然后放入configuration的映射语句集合中（mappedStatements）
   *
   * @param list      包含<select><insert><update><delete>这4种标签
   */
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    // 循环遍历所有的<select><insert><update><delete>标签
    for (XNode context : list) {
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        // ⚠️解析<select><insert><update><delete>标签信息，得到一个MappedStatement对象，然后放入configuration的映射语句集合中（mappedStatements）
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        // 如果出现SQL语句不完整，把它记下来，塞到configuration去
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  /**
   * 重新处理解析失败的resultMap节点
   */
  private void parsePendingResultMaps/* 解析待处理的结果映射 */() {
    /* 1、获取解析失败的resultMap */
    // ⚠️configuration.incompleteResultMaps
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          /* 2、注册ResultMap */
          // ⚠️注册ResultMap
          // 构建ResultMap对象，并添加ResultMap(结果映射)到configuration.resultMaps中
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource... —— ResultMap仍然缺少资源...

        }
      }
    }
  }

  /**
   * 重新处理解析失败的cache-ref节点
   */
  private void parsePendingCacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  /**
   * 重新处理解析失败的sql标签（<select><insert><update><delete>）
   */
  private void parsePendingStatements() {
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  /**
   * 解析<cache-ref>标签
   * >>> 也就是：解析缓存引用：从configuration.caches中，通过引用缓存的命名空间，去获取对应的命名空间的缓存，作为引用缓存
   *
   * 题外：可以使用<cache-ref>标签来引用另外一个命名空间的缓存
   *
   * 参考：<cache-ref namespace="com.someone.application.data.SomeMapper"/>
   */
  private void cacheRefElement(XNode context) {
    if (context != null) {
      /* 1、将当前dao.xml文件中的命名空间，与被引用的缓存所在的命名空间，做一个映射，将它两的映射关系，放入到configuration.cacheRefMap集合中 */
      // 题外：dao.xml文件，也可以称呼为mapper配置文件
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      /* 2、创建缓存引用解析器(CacheRefResolver) */
      // 创建CacheRefResolver对象
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        /* 3、解析缓存引用：从configuration.caches中，通过引用缓存的命名空间，去获取对应的命名空间的缓存，作为引用缓存。如果不存在，就报错。 */
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        /* 4、如果缓存引用过程中出现异常，则添加到Configuration.incompleteCacheRef集合 */
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   * 解析<cache>标签，
   * >>> 也就是：构建Cache对象，然后放入configuration.caches集合中。当前缓存的命名空间，就是当前dao.xml文件的命名空间。
   *
   * 参考：
   * <cache type="PerpetualCache"
   *        eviction="FIFO"
   *        flushInterval="60000"
   *        size="512"
   *        readOnly="true"
   *        blocking="true" >
   *   <property name="name" value="zhangsan"/>
   * </cache>
   */
  private void cacheElement(XNode context) {
    if (context != null) {
      /* 1、获取缓存的类型 */
      // 获取缓存的类型别名，不存在，则默认为PERPETUAL
      String type = context.getStringAttribute("type", "PERPETUAL");
      // 从类型别名集合中，获取缓存的类型别名，对应的Class
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);

      /* 2、获取缓存的驱逐策略 */
      // 获取缓存的驱逐策略对应的类型别名，不存在，则默认为LRU
      String eviction = context.getStringAttribute("eviction"/* 驱逐 */, "LRU");
      // 从类型别名集合中，获取缓存的驱逐策略对应的类型别名，对应的Class
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);

      /* 3、获取缓存的刷新间隔 */
      // 获取缓存的刷新间隔
      Long flushInterval = context.getLongAttribute("flushInterval"/* 刷新间隔 */);

      /* 4、获取缓存的大小 */
      // 获取缓存的大小
      Integer size = context.getIntAttribute("size");

      /* 5、获取缓存是否只读的标识 */
      // 获取缓存是否只读的标识，不存在，则默认为false
      boolean readWrite = !context.getBooleanAttribute("readOnly"/* 只读 */, false);

      /* 6、获取缓存是否阻塞的标识 */
      // 获取缓存是否阻塞的标识，不存在，则默认为false
      boolean blocking = context.getBooleanAttribute("blocking"/* 阻塞 */, false);

      /* 7、获取缓存额外的配置信息，形成Properties —— <cache>下的子标签<property>形成的Properties */
      // 读入额外的配置信息，易于第三方的缓存扩展,例:
      // <cache type="com.domain.something.MyCustomCache">
      //   <property name="cacheFile" value="/tmp/my-custom-cache.tmp"/>
      // </cache>
      Properties props = context.getChildrenAsProperties();

      /*

      8、根据解析到的<cache>标签里面的缓存信息，创建出一个Cache对象（二级缓存，mapper级别），放入configuration.caches集合中。
      题外：当前缓存的命名空间，就是当前dao.xml文件的命名空间

       */
      builderAssistant./* <cache>标签 */useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  /**
   * 配置parameterMap
   * 已经被废弃了，可以忽略，老式风格的参数映射
   */
  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");

      // 类型别名
      String type = parameterMapNode.getStringAttribute("type");
      // 获取别名对应的Class
      Class<?> parameterClass = resolveClass(type);

      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);

        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);

        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }

      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  /**
   * 解析所有的<resultMap>标签（高级功能）
   *
   * @param list    <resultMap>标签集合。因为dao.xml中，<resultMap>标签可以配置很多个，所以是一个list
   */
  private void resultMapElements(List<XNode> list) {
    /*

    1、循环解析<resultMap>标签，
    把每个<resultMap>标签信息，构建成一个ResultMap，然后放入到Configuration的映射结果集合中(Configuration.resultMaps)，
    key是"命名空间+<resultMap>标签id属性"，value就是<resultMap>标签信息构建成的一个ResultMap

     */
    // 循环把resultMap加入到Configuration里去，保持2份，一份缩略，一分全名
    for (XNode resultMapNode : list) {
      try {
        // ⚠️解析单个<resultMap>标签
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  /**
   * 解析<resultMap>标签（单个）
   */
  private ResultMap resultMapElement(XNode resultMapNode) {
    return resultMapElement(resultMapNode, Collections.emptyList(), null);
  }

  /**
   * 解析<resultMap>标签（单个）
   *
   * 参考：
   *    <resultMap id="userResultMap" type="com.msb.mybatis_02.bean.User" autoMapping="false" extends="userMap">
   *         <id property="id" column="user_id" typeHandler="" jdbcType=""/>
   *         <result property="username" column="username" jdbcType="age" typeHandler="age" javaType="age"/>
   *         <association property="user" javaType="user" notNullColumn="" resultSet="" foreignColumn="" fetchType="eager" autoMapping="false"
   *                      select="" columnPrefix="" column="" resultMap="userMap" jdbcType="ARRAY" typeHandler="">
   *             <id column="id" property="id"/>
   *             <result column="username" property="username"/>
   *             <association property="person" javaType="person">
   *                 <id column="id" property="id"/>
   *                 <result column="username" property="username"/>
   *             </association>
   *             <collection property="accounts" ofType="account">
   *                 <id column="aid" property="id"/>
   *                 <result column="uid" property="uid"/>
   *             </collection>
   *             <constructor>
   *                 <idArg column="blog_id" javaType="int" name="" select="" resultMap="" columnPrefix="" jdbcType="" typeHandler=""/>
   *                 <arg column="blog_id" javaType="int" name="" select="" resultMap="" columnPrefix="" jdbcType="" typeHandler=""/>
   *             </constructor>
   *             <discriminator javaType="int" column="haha" typeHandler="" jdbcType="">
   *                 <case value="1" resultType="user" resultMap=""/>
   *             </discriminator>
   *         </association>
   *         <collection property="accounts" ofType="account" javaType="" typeHandler="" jdbcType="" resultMap="" column="" columnPrefix=""
   *                     select="" autoMapping="false" fetchType="" foreignColumn="" notNullColumn="" resultSet="">
   *             <id column="aid" property="id"/>
   *             <result column="uid" property="uid"/>
   *             <association property="user" javaType="user">
   *                 <id column="id" property="id"/>
   *                 <result column="username" property="username"/>
   *             </association>
   *             <collection property="accounts" ofType="account">
   *                 <id column="aid" property="id"/>
   *                 <result column="uid" property="uid"/>
   *             </collection>
   *             <constructor>
   *                 <idArg column="blog_id" javaType="int" name="" select="" resultMap="" columnPrefix="" jdbcType="" typeHandler=""/>
   *                 <arg column="blog_id" javaType="int" name="" select="" resultMap="" columnPrefix="" jdbcType="" typeHandler=""/>
   *             </constructor>
   *             <discriminator javaType="int" column="haha" typeHandler="" jdbcType="">
   *                 <case value="1" resultType="user" resultMap=""/>
   *             </discriminator>
   *         </collection>
   *         <constructor>
   *             <idArg column="blog_id" javaType="int" name="" select="" resultMap="" columnPrefix="" jdbcType="" typeHandler=""/>
   *             <arg column="blog_id" javaType="int" name="" select="" resultMap="" columnPrefix="" jdbcType="" typeHandler=""/>
   *         </constructor>
   *         <discriminator javaType="int" column="haha" typeHandler="" jdbcType="">
   *             <case value="1" resultType="user" resultMap=""/>
   *         </discriminator>
   *     </resultMap>
   *
   * 引用上面定义的<resultMap>：
   *
   *      <select id="getAllUser" resultMap="userMap">
   *       select * from user
   *      </select>
   *
   * 题外：association、collection 具备延迟加载功能
   * 题外：collection是用于建立一对多中集合属性的对应关系
   * >>> ofType用于指定集合元素的数据类型,可以使用别名，也可以使用全限定名
   *
   * @param resultMapNode                 单个<resultMap>标签
   * @param additionalResultMappings      以参数形式，传入的resultMap，一般为null（一般作为嵌套的结果映射）
   * @param enclosingType                 null
   * @return
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType/* 封闭类型 */) {
    // 记录日志
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier/* 获取基于值的标识符 */());

    /* 1、确定<resultMap>的类型 —— type */

    // 获取<resultMap id="" type="">指向的类型（可以是别名，也可以是全限定名）
    String type = resultMapNode.getStringAttribute("type",
      // 一般拿type就可以了，后面3个难道是兼容老的代码？
      resultMapNode.getStringAttribute("ofType",
        resultMapNode.getStringAttribute("resultType",
          resultMapNode.getStringAttribute("javaType"))));

    // 解析类型别名，得到类型（Class）；如果是全限定名，那么返回的就是全限定名对应的类型（Class）
    Class<?> typeClass = resolveClass(type);

    if (typeClass == null) {
      typeClass = inheritEnclosingType/* 继承封闭类型 */(resultMapNode, enclosingType);
    }

    /* 2、解析<resultMap>的子标签 */

    Discriminator discriminator/* 鉴别器 */ = null;

    // 存放所有的ResultMapping
    List<ResultMapping> resultMappings = new ArrayList<>(additionalResultMappings/* 额外的结果映射 */);

    // 获取<resultMap>的所有子标签
    List<XNode> resultChildren = resultMapNode.getChildren();

    // 遍历<resultMap>中的子标签
    for (XNode resultChild : resultChildren) {
      /* 2.1、解析<constructor>标签 */
      if ("constructor"/* 构造器 */.equals(resultChild.getName())) {
        // 注意：⚠️<constructor>标签内部，可以定义和<resultMap>内部一样的子标签，所以最终还是走buildResultMappingFromContext()方法，构建resultMap(结果映射)
        processConstructorElement(resultChild, typeClass, resultMappings);
      }
      /* 2.2、解析<discriminator>标签 */
      else if ("discriminator"/* 鉴别器 */.equals(resultChild.getName())) {
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      }
      /* 2.3、解析其它标签：<id>、<result>、<association>、<collection> */
      else {
        // 结果标识集合
        List<ResultFlag> flags = new ArrayList<>();

        /* 2.3.1、<id> */
        // 判断是不是<id>标签，是的话就加上一个id标识
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }

        /* 2.3.2、解析<result>、<association>、<collection>标签，构建ResultMapping */

        // 构建ResultMapping(结果映射)
        ResultMapping resultMapping = buildResultMappingFromContext/* 从上下文构建结果映射 */(resultChild, typeClass, flags);

        // 添加ResultMapping
        resultMappings.add(resultMapping);
      }
    }

    /* 3、获取<resultMap>的id属性 */

    // 获取<resultMap id="">的id属性
    // 题外：️<association>、<collection>、<case>也会当作<resultMap>标签进行处理的
    // >>> 如果是️<association>、<collection>、<case>标签，是没有id这个属性的，所以走的是resultMapNode.getValueBasedIdentifier()生成id
    String id = resultMapNode.getStringAttribute("id",
      resultMapNode.getValueBasedIdentifier()/* 获取基于值的标识符 */);

    /* 4、获取<resultMap>的extends属性 */

    // 获取<resultMap extends="">的id属性
    // ⚠️extends：继承其它resultMap
    String extend = resultMapNode.getStringAttribute("extends");

    /* 5、获取<resultMap>的autoMapping属性 */

    // 获取<resultMap autoMapping="">的id属性，作用：是否自动映射
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");

    /* 6、创建ResultMap解析器；然后通过"ResultMap解析器"，构建出ResultMap对象，并添加ResultMap(结果映射)到configuration中 */
    // 最后再调ResultMapResolver得到ResultMap
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      // ⚠️通过"ResultMap解析器"，构建出ResultMap对象，并添加ResultMap(结果映射)到configuration中
      return resultMapResolver.resolve();
    } catch (IncompleteElementException e) {

      /*

      7、当目前的configuration.resultMaps中，不存在️extends —— 继承的resultMap时，则抛出该异常，
      然后保存到configuration.incompleteResultMaps中，用于后续，等所有的resultMap加载完成后，
      可能configuration.resultMaps中就存在️extends的resultMap了，那个时候再尝试处理，或许就能成功！

       */
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  protected Class<?> inheritEnclosingType/* 继承封闭类型 */(XNode resultMapNode, Class<?> enclosingType) {
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        return metaResultType.getSetterType(property);
      }
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      return enclosingType;
    }
    return null;
  }

  /**
   * 解析<resultMap>下的<constructor>标签
   *
   * 参考：
   * <constructor>
   *     <idArg column="blog_id" javaType="int" name="" select="" resultMap="" columnPrefix="" jdbcType="" typeHandler=""/>
   *     <arg column="blog_id" javaType="int" name="" select="" resultMap="" columnPrefix="" jdbcType="" typeHandler=""/>
   * </constructor>
   *
   * @param resultChild
   * @param resultType
   * @param resultMappings
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) {
    // 获取<constructor>标签下的所有子标签
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      // 结果标识集合
      List<ResultFlag> flags = new ArrayList<>();

      // 结果标志加上CONSTRUCTOR
      flags.add(ResultFlag.CONSTRUCTOR);

      // 如果存在<idArg>标签，就往结果标志集合中加上ID
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }

      // ⚠️构建resultMap(结果映射)
      ResultMapping resultMapping = buildResultMappingFromContext(argChild, resultType, flags);

      // 添加resultMap(结果映射)
      resultMappings.add(resultMapping);
    }
  }


  /**
   * 解析<resultMap>下的<discriminator>标签
   *
   * 参考：
   * <discriminator javaType="int" column="draft" typeHandler="" jdbcType="">
   *     <case value="1" resultType="user" resultMap=""/>
   * </discriminator>
   *
   * @param context
   * @param resultType          最外层的<resultMap>标签的type属性
   * @param resultMappings
   * @return
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) {
    String column = context.getStringAttribute("column");

    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");

    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);

    Map<String, String> discriminatorMap = new HashMap<>();
    // 遍历所有子标签，<discriminator>下面只有<case>这个子标签
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");

      // <case value="1" resultType="user" resultMap=""/>
      // 处理嵌套的resultMap，得到嵌套的resultMap.id
      String resultMap/* 嵌套的resultMap.id */ = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings, resultType));

      discriminatorMap.put(value, resultMap);
    }

    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }


  /**
   * 解析<sql>标签
   * >>> 也就是：添加sql片段到"sql片段集合(sqlFragments)"中
   *
   * <sql>标签作用：用于定义可重用的SQL代码段
   *
   * 参考：
   * <sql id="defaultSql">
   *     select * from user
   * </sql>
   */
  private void sqlElement(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  /**
   * 解析<sql>标签
   * >>> 也就是：添加sql片段到"sql片段集合(sqlFragments)"中
   *
   * <sql>标签作用：用于抽取重复的SQL代码
   *
   * 参考：
   * <sql id="defaultSql">
   *     select * from user
   * </sql>
   * or
   * <sql id="userColumns" databaseId="">
   *   id,username,password
   * </sql>
   *
   */
  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {

      // 获取数据库id
      String databaseId = context.getStringAttribute("databaseId");
      // 获取该sql代码片段的id标识
      String id = context.getStringAttribute("id");

      // id = 当前命名空间.id
      // 将"当前命名空间."与"sql代码片段id"进行拼接，得到该sql代表片段的唯一标识
      id = builderAssistant.applyCurrentNamespace(id, false);

      // 判断是否可以添加当前sql片段
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        // 将sql片段放入"sql片段集合(sqlFragments)"
        // 注意：此时还没有解析sql片段，直接将XNode放进去了
        sqlFragments.put(id, context);
      }

    }
  }

  /**
   * 判断是否可以添加当前sql片段
   *
   * @param id                    <sql>代码片段的唯一标识，由"命名空间.id"组成
   * @param databaseId            <sql>标签中定义的数据库id
   * @param requiredDatabaseId    当前数据库id
   * @return
   */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    if (databaseId != null) {
      return false;
    }

    // 判断sql片段集合中，是否包含当前sql片段id
    // 如果不包含当前sql片段id，就返回true，代表可以添加
    if (!this.sqlFragments.containsKey(id)) {
      return true;
    }
    // skip this fragment if there is a previous one with a not null databaseId
    // 上面的翻译：如果前一个片段的 databaseId 不为空，则跳过此片段

    // 如果包含，有重名的id，就获取已经存在的sql的databaseId，
    // 如果databaseId为null，也就是说不存在databaseId，则返回true，代表允许覆盖老的sql；
    // 否则，如果存在databaseId，就返回false，代表不允许覆盖
    XNode context = this.sqlFragments.get(id);
    return context.getStringAttribute("databaseId") == null;
  }

  /**
   * 解析<result>、<association>、<collection>标签
   *
   *  <result property="username" column="username" jdbcType="age" typeHandler="age" javaType="age"/>
   *  <association property="user" javaType="user" notNullColumn="" resultSet="" foreignColumn="" fetchType="eager" autoMapping="false"
   *               select="" columnPrefix="" column="" resultMap="userMap" jdbcType="ARRAY" typeHandler="">
   *      <id column="id" property="id"/>
   *      <result column="username" property="username"/>
   *      <association property="person" javaType="person">
   *          <id column="id" property="id"/>
   *          <result column="username" property="username"/>
   *      </association>
   *      <collection property="accounts" ofType="account">
   *          <id column="aid" property="id"/>
   *          <result column="uid" property="uid"/>
   *      </collection>
   *      <constructor>
   *          <idArg column="blog_id" javaType="int" name="" select="" resultMap="" columnPrefix="" jdbcType="" typeHandler=""/>
   *          <arg column="blog_id" javaType="int" name="" select="" resultMap="" columnPrefix="" jdbcType="" typeHandler=""/>
   *      </constructor>
   *      <discriminator javaType="int" column="haha" typeHandler="" jdbcType="">
   *          <case value="1" resultType="user" resultMap=""/>
   *      </discriminator>
   *  </association>
   *  <collection property="accounts" ofType="account" javaType="" typeHandler="" jdbcType="" resultMap="" column="" columnPrefix=""
   *              select="" autoMapping="false" fetchType="" foreignColumn="" notNullColumn="" resultSet="">
   *      <id column="aid" property="id"/>
   *      <result column="uid" property="uid"/>
   *      <association property="user" javaType="user">
   *          <id column="id" property="id"/>
   *          <result column="username" property="username"/>
   *      </association>
   *      <collection property="accounts" ofType="account">
   *          <id column="aid" property="id"/>
   *          <result column="uid" property="uid"/>
   *      </collection>
   *      <constructor>
   *          <idArg column="blog_id" javaType="int" name="" select="" resultMap="" columnPrefix="" jdbcType="" typeHandler=""/>
   *          <arg column="blog_id" javaType="int" name="" select="" resultMap="" columnPrefix="" jdbcType="" typeHandler=""/>
   *      </constructor>
   *      <discriminator javaType="int" column="haha" typeHandler="" jdbcType="">
   *          <case value="1" resultType="user" resultMap=""/>
   *      </discriminator>
   *  </collection>
   *
   * 构建ResultMapping(结果映射)
   *
   * @param context
   * @param resultType    <resultMap type="">结果映射类型
   * @param flags
   *
   * @return
   */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) {
    //<id property="id" column="author_id"/>
    //<result property="username" column="author_username"/>
    String property;

    // 判断<resultMap>标签中是否存在<constructor>标签
    // 题外：ResultFlag.CONSTRUCTOR标识，会在解析<constructor>标签的时候添加，如果存在<constructor>标签，就会添加
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }

    // column
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");

    /*

    ⚠️处理嵌套的resultMap（结果映射），返回ResultMap.id，供引用：
    (1)先获取当前标签中的resultMap属性值，作为ResultMap.id
    (2)如果当前标签没有配置resultMap属性，则处理<association>、<collection>、<case>这3个标签
    >>> 因为这3个标签可以配置<resultMap>标签下的所有子标签，所以将这3个标签当作<resultMap>标签进行解析，构建对应的对应的ResultMap，
    >>> 因为这3个标签是作为<resultMap>标签中的<resultMap>标签进行处理，所以也是作为嵌套的resultMap，返回ResultMap.id，
    >>> 同时因为这3个标签是作为嵌套的resultMap，所以即使这3个标签没有配置resultMap属性，其对应的ResultMapping中会有对应的nestedResultMapId

     */
    String nestedResultMap = context.getStringAttribute("resultMap", () ->
      processNestedResultMappings(context, Collections.emptyList(), resultType));

    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");

    String resultSet = context.getStringAttribute("resultSet");

    String foreignColumn = context.getStringAttribute("foreignColumn");
    // 先根据fetchType确认是否延迟加载；如果没有配置fetchType，则根据configuration.lazyLoadingEnabled全局配置来决定是否延迟加载
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled()/* 是否启用延迟加载 */ ? "lazy" : "eager"));

    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);

    // 用标签信息构建ResultMapping
    return builderAssistant.buildResultMapping/* <result>、<association>、<collection> */(resultType, property, column,
      javaTypeClass, jdbcTypeEnum, nestedSelect,
      // resultMap属性（嵌套的resultMap），
      // 例如：<collection resultMap="role"/>中的resultMap
      nestedResultMap,
      notNullColumn, columnPrefix,
      typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  /**
   * 处理嵌套的resultMap（结果映射）：
   * 将<association>、<collection>、<case>这3个标签，当作<resultMap>标签进行解析，得出嵌套的ResultMap，
   * 然后返回的是ResultMap.id，供引用
   *
   * 例如：
   * （1）
   * <discriminator javaType="int" column="draft" typeHandler="" jdbcType="">
   *     <case value="1" resultType="user" resultMap=""/>
   * </discriminator>
   *
   * （2）
   * <collection property="accounts" ofType="account" javaType="" resultMap=""></collection>
   *
   * （3）
   * <association property="user" javaType="user" resultMap="userMap"></association>
   *
   * @param context           当前标签，例如：<association foreignColumn="id" resultSet="ahahahah" column="uid" property="user" javaType="com.hm.m_04.entity.User"/>
   * @param resultMappings    空对象
   * @param enclosingType     结果类型 —— <resultMap>标签中的type属性
   * @return
   */
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) {

    /* 1、判断一下当前标签是不是<association>、<collection>、<case>这3个标签中的一个，并且不包含select属性，是的话就处理 */
    // 判断一下当前标签是不是<association>、<collection>、<case>这3个标签中的一个，并且不包含select属性，是的话就处理
    // 因为只有<association>、<collection>、<case>这3个标签有嵌套的resultMap，
    // 并且需要是不包含select属性，因为包含了select属性，就代表是查询另一个语句了
    if (Arrays.asList("association", "collection", "case").contains(context.getName())
      && context.getStringAttribute("select") == null) {

      /*

      2、验证，如果是<collection>标签的话，判断结果映射类中，是否包含，<collection>标签的property属性，所对应的set方法
      也就是说：判断结果映射类中，是否包含，<collection>标签的property属性，所对应的set方法
      >>> 因为只有对应的set方法，才能设置对应的属性值进去！

       */
      validateCollection(context, enclosingType);

      /*

      3、将<association>、<collection>、<case>这3个标签，会当作<resultMap>标签进行解析，
      也就是直接把这3个标签当做当前<resultMap>中嵌套的resultMap进行处理，得出ResultMap

      注意：⚠️因为️<association>、<collection>、<case>这3个标签可以配置<resultMap>标签下的所有子标签，所以将这3个标签当作<resultMap>标签进行解析，构建对应的对应的ResultMap，
      因为这3个标签是作为<resultMap>标签中的<resultMap>标签进行处理，所以也是作为嵌套的resultMap，返回ResultMap.id，
      同时因为这3个标签是作为嵌套的resultMap，所以即使这3个标签没有配置resultMap属性，其对应的ResultMapping中会有对应的nestedResultMapId

       */
      // ⚠️递归调用该方法，构建ResultMap
      ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
      return resultMap.getId();
    }
    return null;
  }

  /**
   * 验证，如果是<collection>标签的话，判断结果映射类中，是否包含，<collection>标签的property属性，所对应的set方法
   * 也就是说：判断结果映射类中，是否包含，<collection>标签的property属性，所对应的set方法
   * >>> 因为只有对应的set方法，才能设置对应的属性值进去！
   *
   * @param context
   * @param enclosingType   最外层的<resultMap>标签的type属性
   */
  protected void validateCollection(XNode context, Class<?> enclosingType) {
    // 【是<collection>标签 && resultMap属性为null && javaType属性也为null】则条件成立
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
      && context.getStringAttribute("javaType") == null) {

      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      // 获取<collection>标签的property属性
      String property = context.getStringAttribute("property");

      // 判断最外层的<resultMap>标签的type属性，是不是包含，当前<collection>标签的property属性，所对应的set方法
      // 也就是说：判断结果映射类中，是否包含，<collection>标签的property属性，所对应的set方法
      // >>> 因为只有对应的set方法，才能设置对应的属性值进去！
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
          // 属性“”+属性+“”的不明确集合类型。您必须指定“javaType”或“resultMap”。
          "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
      }
    }
  }

  /**
   * 判断一下，是否已经注册了当前命名空间所对应的mapper(configuration.mapperRegistry.knownMappers中是否存在当前命名空间所对应的mapper接口)
   * 如果被加载过了，则不做任何事情；
   * 如果未被加载，就注册对应的mapper
   */
  private void bindMapperForNamespace() {
    // 获取当前命名空间
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        // 解析命名空间对应的类型
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        // ignore, bound type is not required —— 忽略，不需要绑定类型
      }

      /* 1、判断一下，是否已经注册了当前命名空间所对应的mapper */

      // 判断configuration.mapperRegistry.knownMappers中是否存在当前命名空间所对应的mapper接口
      // 也就是：判断是否己经加载了当前命名空间所对应的mapper接口，加载过了，就什么都不做；没加载过的话，就注册对应mapper
      if (boundType != null &&
        !configuration.hasMapper(boundType)) {

        /* 2、如果未被加载，就注册对应的mapper */

        // Spring may not know the real resource name so we set a flag
        // to prevent loading again this resource from the mapper interface
        // look at MapperAnnotationBuilder#loadXmlResource
        // 上面翻译：Spring可能不知道真正的资源名称，因此我们设置一个标志，以防止从映射器界面再次加载此资源，查看：MapperAnnotationBuilder#loadXmlResource()

        // 追加namespace前级，添加到configuration.loadedResources集合中保存，表示该资源，已经被加载过了，注册对应mapper了
        configuration.addLoadedResource("namespace:" + namespace);

        /**
         * 注册mapper：
         * 会判断，必须是接口，才会进行加载和解析
         * 1、先是构建mapper接口对应的MapperProxyFactory，用于生成mapper接口的代理对象；并将它两的对应关系，存入knownMappers（已知mapper）集合中
         * 2、然后去解析mapper，分为2部分
         * （1）先解析mapper接口对应的dao.xml文件，将对应信息放入Configuration；—— 配置文件开发
         * （2）然后再解析mapper接口（把mapper接口作为映射文件进行解析），将对应信息放入Configuration—— 注解开发
         */
        // ⚠️注册mapper
        configuration.addMapper(boundType);
      }
    }
  }

}
