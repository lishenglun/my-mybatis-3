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
package org.apache.ibatis.session;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;

import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.builder.annotation.MethodResolver;
import org.apache.ibatis.builder.xml.XMLStatementBuilder;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.FifoCache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.decorators.SoftCache;
import org.apache.ibatis.cache.decorators.WeakCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.datasource.jndi.JndiDataSourceFactory;
import org.apache.ibatis.datasource.pooled.PooledDataSourceFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSourceFactory;
import org.apache.ibatis.executor.BatchExecutor;
import org.apache.ibatis.executor.CachingExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ReuseExecutor;
import org.apache.ibatis.executor.SimpleExecutor;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.executor.loader.cglib.CglibProxyFactory;
import org.apache.ibatis.executor.loader.javassist.JavassistProxyFactory;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl;
import org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl;
import org.apache.ibatis.logging.log4j.Log4jImpl;
import org.apache.ibatis.logging.log4j2.Log4j2Impl;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMap;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.InterceptorChain;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.scripting.LanguageDriverRegistry;
import org.apache.ibatis.scripting.defaults.RawLanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * mybatis配置对象：mybatis配置文件当中的所有配置项，都会放入到这个对象中
 *
 * @author Clinton Begin
 */
public class Configuration {

  // 数据库环境对象，里面包含：数据库环境id、事务工厂、数据源
  protected Environment environment;

  //---------以下都是<settings>节点-------

  /* 要想知道以下字段含义，可以在mybatis官网查看(https://mybatis.org/mybatis-3/zh/configuration.html) */

  protected boolean safeRowBoundsEnabled;
  protected boolean safeResultHandlerEnabled = true;
  // 是否开启驼峰的标识（true：启用，false：不启用）
  protected boolean mapUnderscoreToCamelCase;
  // true：调用任意方法都会立即加载对象的所有"延迟加载属性"；
  // false：每个"延迟加载属性"按需加载
  protected boolean aggressiveLazyLoading;
  protected boolean multipleResultSetsEnabled = true;

  protected boolean useGeneratedKeys;
  protected boolean useColumnLabel = true;
  /**
   * 1、在官网中，对该属性的描述是：全局性地开启或关闭所有映射器配置文件中已配置的任何缓存，
   * 翻译成白话就是：全局性地开启或关闭所有mapper配置文件中已配置的任何缓存，
   * 再进一步翻译成白话：全局性地开启或关闭所有mapper级别的缓存
   * 而mapper级别的缓存就是俗称的二级缓存，所以再进一步翻译成白话：全局性的开启或关闭二级缓存
   *
   * 2、如果不根据官网的描述，从使用处，也能推断出这个配置的含义：
   *
   * 该配置项，唯一使用的地方是在，Configuration#newExecutor()，里面是根据该配置项，决定是否改变当前的Executor对象为CachingExecutor，
   * 而只有在CachingExecutor#query()中才会操作二级缓存的逻辑！剩余的Executor是没有的；
   * 剩余的Executor，走的是BaseExecutor，在BaseExecutor#query()中，只会操作一级缓存！
   * 所以当【cacheEnabled = true】，会增强当前的Executor对象为CachingExecutor，CachingExecutor是在原先的Executor基础之上增加了二级缓存的功能！
   * ⚠️如果此选项配置为false，即使在mapper文件中配置了<cache>，那么mapper文件对应的二级缓存也不会生效！
   */
  // 全局性的开启或关闭二级缓存，也就是：是否开启二级缓存的标识（true：启用，false：不启用）
  // 注意：⚠️如果此选项配置为false，即使在mapper文件中配置了<cache>，那么mapper文件对应的二级缓存也不会生效！
  // 题外：这里是全局的开或关二级缓存，而<cache>则是更细粒度的单独开或关某一mapper文件所对应的二级缓存！
  protected boolean cacheEnabled = true;
  protected boolean callSettersOnNulls;
  // 是否"使用实际参数名称"的标识
  protected boolean useActualParamName = true;
  protected boolean returnInstanceForEmptyRow;
  protected boolean shrinkWhitespacesInSql;
  protected boolean nullableOnForEach;
  // 默认值false
  protected boolean argNameBasedConstructorAutoMapping;

  protected String logPrefix;
  protected Class<? extends Log> logImpl;
  protected Class<? extends VFS> vfsImpl;
  protected Class<?> defaultSqlProviderType;
  // 本地缓存范围
  protected LocalCacheScope localCacheScope = LocalCacheScope.SESSION;
  protected JdbcType jdbcTypeForNull = JdbcType.OTHER;
  // 指定对象的哪些方法触发一次延迟加载（equals,clone,hashCode,toString）
  // 题外：修改这里没用，因为在XMLConfigBuilder#settingsElement()中，还是会设置默认值为"equals", "clone", "hashCode", "toString"
  protected Set<String> lazyLoadTriggerMethods = new HashSet<>(Arrays.asList("equals", "clone", "hashCode", "toString"));
  protected Integer defaultStatementTimeout;
  // 读取条数
  protected Integer defaultFetchSize;
  protected ResultSetType defaultResultSetType;
  // 默认为简单执行器
  protected ExecutorType defaultExecutorType = ExecutorType.SIMPLE;

  // 只自动映射结果，不会映射嵌套结果
  protected AutoMappingBehavior autoMappingBehavior = AutoMappingBehavior.PARTIAL;

  protected AutoMappingUnknownColumnBehavior autoMappingUnknownColumnBehavior = AutoMappingUnknownColumnBehavior.NONE;

  //---------以上都是<settings>节点-------

  // 属性
  protected Properties variables = new Properties();
  // 反射工厂
  protected ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
  // 对象工厂：用于指定结果集对象的实例是如何创建的
  protected ObjectFactory objectFactory = new DefaultObjectFactory();
  // 对象包装器工厂
  protected ObjectWrapperFactory objectWrapperFactory = new DefaultObjectWrapperFactory();

  // 是否启用延迟加载（默认禁用延迟加载）
  protected boolean lazyLoadingEnabled = false;
  // 代理工厂
  protected ProxyFactory proxyFactory = new JavassistProxyFactory(); // #224 Using internal Javassist instead of OGNL

  // 数据库id
  // 题外：这个一般为null，不会去配置
  protected String databaseId;

  /**
   * Configuration factory class.
   * Used to create Configuration for loading deserialized unread properties.
   *
   * @see <a href='https://github.com/mybatis/old-google-code-issues/issues/300'>Issue 300 (google code)</a>
   */
  protected Class<?> configurationFactory;

  // 映射器注册器(或者叫：mapper注册器)
  protected final MapperRegistry mapperRegistry/* 映射器注册表 */ = new MapperRegistry(this);
  // 拦截器链
  protected final InterceptorChain interceptorChain = new InterceptorChain();
  // 类型处理器注册器
  protected final TypeHandlerRegistry typeHandlerRegistry = new TypeHandlerRegistry(this);
  // 类型别名注册器
  // 题外：类的别名规则：有@Alias注解则用，没有则取类的simpleName
  protected final TypeAliasRegistry typeAliasRegistry = new TypeAliasRegistry();
  // 脚本语言注册器
  protected final LanguageDriverRegistry languageRegistry = new LanguageDriverRegistry();

  /**
   * 1、key：标签的唯一标识 = MappedStatement.id属性值 = mapper接口全限定名+方法名(或者叫：包名+类名+方法名)，例如：com.msb.mybatis_02.dao.UserDao.getUser
   * >>> 也有可能是"mapper接口全限定名+方法名+!selectKey"，例如：com.msb.mybatis_02.dao.UserDao.insert!selectKey
   *
   * 2、value：MappedStatement，里面包含了sql语句
   *
   * 3、题外：MappedStatement存放了我们的sql语句，参数类型，resultMap集合、sql命令类型、执行sql的对象类型，等其它信息
   *
   * 4、题外：我们定义的是接口，接口方法要跟配置文件里面的sql语句映射起来、绑定起来，这样我们在调用接口方法时，才能执行到对应的sql语句！
   */
  // 映射语句集合（里面包含了一大堆信息，比如：sql语句、参数类型，resultMap集合（之前解析好的，这里通过resultMapId获取到）、sql命令类型、执行sql的对象类型，等信息）
  protected final Map<String, MappedStatement> mappedStatements/* 映射语句 */ = new StrictMap<MappedStatement>("Mapped Statements collection")
    .conflictMessageProducer((savedValue, targetValue) ->
      ". please check " + savedValue.getResource() + " and " + targetValue.getResource());

  /**
   * 1、注意：如果dao.xml中配置<cache>，然后dao接口上也配置@CacheNamespace。
   * >>> 会先使用dao.xml中配置的<cache>标签信息构建一个Cache注册到configuration.caches集合中，
   * >>> 后续，在解析dao接口的时候，也会使用@CacheNamespace信息构建一个Cache，但是放入到configuration.caches集合中的时候，会报错，
   * >>> 因为内部逻辑会判断是否已经存在相同Cache.id的Cache，也就是相同命名空间的Cache（因为Cache.id=命名空间，或者称呼为接口全限定名也行），存在就报错！
   *
   * 2、注意：我们注册一个Cache到configuration.caches集合中，会保存一个Cache的2份数据到configuration.caches集合中。
   * >>> value相同，都是同一个Cache；但是key不同，一个key是命名空间(接口全限定名)，一个key是命名空间对应的接口名称简写(接口名)
   * >>> 如果存在接口名称简写的value值，则以Ambiguity来替代，代表有歧义！
   * >>> >>> 例如：我注册com.hm.dao.UserDao的Cache@1628到caches中，会保存2份数据：一份【key=com.hm.UserDao，value=Cache@1628】，另一份【key=UserDao，value=Cache@1628】；
   * >>> >>> 接着我再注册com.msb.UserDao的Cache@2322到caches中，会保存一份【key=com.msb.UserDao，value=Cache@2322】，接着我再保存【key=UserDao，value=Cache@2322】时，
   * >>> >>> 发现key相同了，那就会把value值替换为Ambiguity，变为【key=UserDao，value=Ambiguity】进行保存，表示UserDao这个key的value值有多个，有歧义！后续可以根据value值做判断，看是不是存在多个，是不是有歧义！
   */
  // 二级缓存集合
  // key：命名空间（也就是mapper接口的全限定名）
  // value：Cache
  protected final Map<String, Cache> caches = new StrictMap<>("Caches collection");

  /**
   * 1、key：有2种形式：1、命名空间 + <resultMap>的id属性；2、<resultMap>的id属性
   *
   * 例如：
   * <mapper namespace="com.msb.other.resultSets.t_02.dao.AccountDao">
   *
   *   <resultMap id="accountMap" type="com.msb.other.resultSets.t_02.entity.Account" >
   *     <id column="id" property="id"/>
   *     <result column="uid" property="uid"/>
   *   </resultMap>
   *
   * </mapper>
   *
   * 它会生成一个ResultMap对象，但是会存储2份数据到resultMaps中，这2份数据的ResultMap对象都一样，
   * 但是key不同，有一个key叫做accountMap；另一个key叫做com.msb.other.resultSets.t_02.dao.AccountDao.accountMap
   *
   * 总结：⚠️我们会存储一个ResultMap对象的2份数据到configuration.resultMaps中，只是key不同，一个key是【<resultMap>的id属性】，一个是【命名空间+.+<resultMap>的id属性】
   *
   * 2、value：<resultMap>标签的内容，构成的ResultMap
   *
   * 题外：<collection>、<association>、<case>标签也会生成ResultMap
   *
   */
  // 结果映射集合（也就是<resultMap>标签的内容）
  protected final Map<String, ResultMap> resultMaps = new StrictMap<>("Result Maps collection");
  // 参数映射集合
  protected final Map<String, ParameterMap> parameterMaps = new StrictMap<>("Parameter Maps collection");
  // key生成器集合（也就是<selectKey>标签的内容）
  // key = mapper接口全限定名+方法名+!selectKey，例如：com.msb.mybatis_02.dao.UserDao.insert!selectKey
  // value = KeyGenerator，里面包含了<selectKey>标签中的sql语句
  protected final Map<String, KeyGenerator> keyGenerators = new StrictMap<>("Key Generators collection");

  // 记录己经加载过的映射文件（记录已经加载过的mapper）
  // 有三种数据：1、mapper接口全限定名；2、mapper接口对应的dao.xml文件路径；3、namespace:currentNamespace
  protected final Set<String> loadedResources = new HashSet<>();
  protected final Map<String, XNode> sqlFragments = new StrictMap<>("XML fragments parsed from previous mappers"/* 从以前的映射器解析的XML片段 */);

  // 解析<select><insert><update><delete>标签，构建sql语句时出错，就把标签对应的XMLStatementBuilder放入到这里！
  protected final Collection<XMLStatementBuilder> incompleteStatements/* 不完整的SQL语句 */ = new LinkedList<>();
  /**
   * 引用缓存的2种方式：
   * 1、@CacheNamespaceRef
   * 2、<cache-ref namespace=""/>
   * 也就是在解析这2种方式，获取引用缓存的时候，报错，然后将对应的CacheRefResolver添加到该集合中
   */
  // 从configuration.caches中，通过引用缓存的命名空间，去获取对应的命名空间的缓存，作为引用缓存。如果不存在，就报错，报错后，其对应的CacheRefResolver就放入到这里了！
  protected final Collection<CacheRefResolver> incompleteCacheRefs/* 不完整的缓存引用 */ = new LinkedList<>();
  // 解析<resultMap>标签时，然后构建ResultMap时出错，就把<resultMap>标签对应的ResultMapResolver，放到这里
  protected final Collection<ResultMapResolver> incompleteResultMaps/* 不完整的resultMap */ = new LinkedList<>();
  // 解析resultMap相关标签时，出错，则把对应的MethodResolver，放到这里
  protected final Collection<MethodResolver> incompleteMethods = new LinkedList<>();

  /*
   * A map holds cache-ref relationship. The key is the namespace that
   * references a cache bound to another namespace and the value is the
   * namespace which the actual cache is bound to.
   */

  // 存放的是：当前dao.xml文件中的命名空间，与被引用的缓存所在的命名空间，的映射关系
  // key：书写<cache-ref>标签，引入缓存的dao.xml的命名空间
  // value：被引入缓存的命名空间
  // 参考：<cache-ref>引入的cache内容：<cache-ref namespace="com.someone.application.data.SomeMapper"/>
  protected final Map<String, String> cacheRefMap = new HashMap<>();

  public Configuration(Environment environment) {
    this();
    this.environment = environment;
  }

  public Configuration() {

    // ⚠️typeAliasRegistry.registerAlias()都是注册类型别名
    // 我们这些最基本的配置项，是要写在配置文件中的，为了不让大家在配置文件里面写得很复杂，所以给出了一些别名来进行简写
    // 当我们写完别名简写之后，可以通过这些别名映射器，把别名简写映射成具体的实体类对象
    // 例如：JDBC、POOLED
    // <environments default="development">
    //     <environment id="development">
    //         <transactionManager type="JDBC"/>
    //         <dataSource type="POOLED">
    //             <property name="driver" value="${driver}"/>
    //             <property name="url" value="${url}"/>
    //             <property name="username" value="${username}"/>
    //             <property name="password" value="${password}"/>
    //         </dataSource>
    //     </environment>
    // </environments>

    /* 事务工厂（事务管理器） */
    typeAliasRegistry.registerAlias("JDBC", JdbcTransactionFactory.class);
    typeAliasRegistry.registerAlias("MANAGED", ManagedTransactionFactory.class);

    /* 数据源 */
    typeAliasRegistry.registerAlias("JNDI", JndiDataSourceFactory.class);
    typeAliasRegistry.registerAlias("POOLED", PooledDataSourceFactory.class);
    typeAliasRegistry.registerAlias("UNPOOLED", UnpooledDataSourceFactory.class);

    /* 缓存 */
    /**
     * 题外：在dao.xml文件中，我们还可以配置对应的缓存：
     * <mapper namespace="com.msb.mybatis_02.dao.UserDao">
     *   <cache></cache>
     *   <cache-ref></cache-ref>
     * </mapper>
     */
    typeAliasRegistry.registerAlias("PERPETUAL", PerpetualCache.class);
    typeAliasRegistry.registerAlias("FIFO", FifoCache.class);
    typeAliasRegistry.registerAlias("LRU", LruCache.class);
    typeAliasRegistry.registerAlias("SOFT", SoftCache.class);
    typeAliasRegistry.registerAlias("WEAK", WeakCache.class);

    /* 数据库供应商 */
    typeAliasRegistry.registerAlias("DB_VENDOR", VendorDatabaseIdProvider.class);

    /* 语言驱动器 */
    typeAliasRegistry.registerAlias("XML", XMLLanguageDriver.class);
    typeAliasRegistry.registerAlias("RAW", RawLanguageDriver.class);

    /* 日志 */
    typeAliasRegistry.registerAlias("SLF4J", Slf4jImpl.class);
    typeAliasRegistry.registerAlias("COMMONS_LOGGING", JakartaCommonsLoggingImpl.class);
    typeAliasRegistry.registerAlias("LOG4J", Log4jImpl.class);
    typeAliasRegistry.registerAlias("LOG4J2", Log4j2Impl.class);
    typeAliasRegistry.registerAlias("JDK_LOGGING", Jdk14LoggingImpl.class);
    typeAliasRegistry.registerAlias("STDOUT_LOGGING", StdOutImpl.class);
    typeAliasRegistry.registerAlias("NO_LOGGING", NoLoggingImpl.class);

    /* 动态代理 */
    typeAliasRegistry.registerAlias("CGLIB", CglibProxyFactory.class);
    typeAliasRegistry.registerAlias("JAVASSIST", JavassistProxyFactory.class);

    /* 语言驱动器 */
    languageRegistry.setDefaultDriverClass(XMLLanguageDriver.class);
    // 注册语言驱动器
    languageRegistry.register(RawLanguageDriver.class);
  }

  public String getLogPrefix() {
    return logPrefix;
  }

  public void setLogPrefix(String logPrefix) {
    this.logPrefix = logPrefix;
  }

  public Class<? extends Log> getLogImpl() {
    return logImpl;
  }

  public void setLogImpl(Class<? extends Log> logImpl) {
    if (logImpl != null) {
      this.logImpl = logImpl;
      LogFactory.useCustomLogging(this.logImpl);
    }
  }

  public Class<? extends VFS> getVfsImpl() {
    return this.vfsImpl;
  }

  public void setVfsImpl(Class<? extends VFS> vfsImpl) {
    if (vfsImpl != null) {
      this.vfsImpl = vfsImpl;
      VFS.addImplClass(this.vfsImpl);
    }
  }

  /**
   * Gets an applying type when omit a type on sql provider annotation(e.g. {@link org.apache.ibatis.annotations.SelectProvider}).
   *
   * @return the default type for sql provider annotation
   * @since 3.5.6
   */
  public Class<?> getDefaultSqlProviderType() {
    return defaultSqlProviderType;
  }

  /**
   * Sets an applying type when omit a type on sql provider annotation(e.g. {@link org.apache.ibatis.annotations.SelectProvider}).
   *
   * @param defaultSqlProviderType the default type for sql provider annotation
   * @since 3.5.6
   */
  public void setDefaultSqlProviderType(Class<?> defaultSqlProviderType) {
    this.defaultSqlProviderType = defaultSqlProviderType;
  }

  public boolean isCallSettersOnNulls() {
    return callSettersOnNulls;
  }

  public void setCallSettersOnNulls(boolean callSettersOnNulls) {
    this.callSettersOnNulls = callSettersOnNulls;
  }

  /**
   * 是否"使用实际参数名称"的标识
   */
  public boolean isUseActualParamName() {
    return useActualParamName;
  }

  public void setUseActualParamName(boolean useActualParamName) {
    this.useActualParamName = useActualParamName;
  }

  public boolean isReturnInstanceForEmptyRow() {
    return returnInstanceForEmptyRow;
  }

  public void setReturnInstanceForEmptyRow(boolean returnEmptyInstance) {
    this.returnInstanceForEmptyRow = returnEmptyInstance;
  }

  public boolean isShrinkWhitespacesInSql() {
    return shrinkWhitespacesInSql;
  }

  public void setShrinkWhitespacesInSql(boolean shrinkWhitespacesInSql) {
    this.shrinkWhitespacesInSql = shrinkWhitespacesInSql;
  }

  /**
   * Sets the default value of 'nullable' attribute on 'foreach' tag.
   *
   * @param nullableOnForEach If nullable, set to {@code true}
   * @since 3.5.9
   */
  public void setNullableOnForEach(boolean nullableOnForEach) {
    this.nullableOnForEach = nullableOnForEach;
  }

  /**
   * Returns the default value of 'nullable' attribute on 'foreach' tag.
   *
   * <p>Default is {@code false}.
   *
   * @return If nullable, set to {@code true}
   * @since 3.5.9
   */
  public boolean isNullableOnForEach() {
    return nullableOnForEach;
  }

  public boolean isArgNameBasedConstructorAutoMapping() {
    return argNameBasedConstructorAutoMapping;
  }

  public void setArgNameBasedConstructorAutoMapping(boolean argNameBasedConstructorAutoMapping) {
    this.argNameBasedConstructorAutoMapping = argNameBasedConstructorAutoMapping;
  }

  public String getDatabaseId() {
    return databaseId;
  }

  public void setDatabaseId(String databaseId) {
    this.databaseId = databaseId;
  }

  public Class<?> getConfigurationFactory() {
    return configurationFactory;
  }

  public void setConfigurationFactory(Class<?> configurationFactory) {
    this.configurationFactory = configurationFactory;
  }

  public boolean isSafeResultHandlerEnabled() {
    return safeResultHandlerEnabled;
  }

  public void setSafeResultHandlerEnabled(boolean safeResultHandlerEnabled) {
    this.safeResultHandlerEnabled = safeResultHandlerEnabled;
  }

  public boolean isSafeRowBoundsEnabled() {
    return safeRowBoundsEnabled;
  }

  public void setSafeRowBoundsEnabled(boolean safeRowBoundsEnabled) {
    this.safeRowBoundsEnabled = safeRowBoundsEnabled;
  }

  public boolean isMapUnderscoreToCamelCase() {
    return mapUnderscoreToCamelCase;
  }

  public void setMapUnderscoreToCamelCase(boolean mapUnderscoreToCamelCase) {
    this.mapUnderscoreToCamelCase = mapUnderscoreToCamelCase;
  }

  public void addLoadedResource(String resource) {
    loadedResources.add(resource);
  }

  /**
   * 检测是否己经加载过该mapper接口
   */
  public boolean isResourceLoaded(String resource) {
    return loadedResources.contains(resource);
  }

  public Environment getEnvironment() {
    return environment;
  }

  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  /**
   * 获取自动映射行为，默认为AutoMappingBehavior.PARTIAL，代表只映射结果，不会映射嵌套的结果
   *
   * @return
   */
  public AutoMappingBehavior getAutoMappingBehavior() {
    return autoMappingBehavior;
  }

  public void setAutoMappingBehavior(AutoMappingBehavior autoMappingBehavior) {
    this.autoMappingBehavior = autoMappingBehavior;
  }

  /**
   * Gets the auto mapping unknown column behavior.
   *
   * @return the auto mapping unknown column behavior
   * @since 3.4.0
   */
  public AutoMappingUnknownColumnBehavior getAutoMappingUnknownColumnBehavior() {
    return autoMappingUnknownColumnBehavior;
  }

  /**
   * Sets the auto mapping unknown column behavior.
   *
   * @param autoMappingUnknownColumnBehavior the new auto mapping unknown column behavior
   * @since 3.4.0
   */
  public void setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior autoMappingUnknownColumnBehavior) {
    this.autoMappingUnknownColumnBehavior = autoMappingUnknownColumnBehavior;
  }

  /**
   * 是否启用延迟加载
   */
  public boolean isLazyLoadingEnabled() {
    return lazyLoadingEnabled;
  }

  public void setLazyLoadingEnabled(boolean lazyLoadingEnabled) {
    this.lazyLoadingEnabled = lazyLoadingEnabled;
  }

  public ProxyFactory getProxyFactory() {
    return proxyFactory;
  }

  public void setProxyFactory(ProxyFactory proxyFactory) {
    if (proxyFactory == null) {
      proxyFactory = new JavassistProxyFactory();
    }
    this.proxyFactory = proxyFactory;
  }

  public boolean isAggressiveLazyLoading() {
    return aggressiveLazyLoading;
  }

  public void setAggressiveLazyLoading(boolean aggressiveLazyLoading) {
    this.aggressiveLazyLoading = aggressiveLazyLoading;
  }

  public boolean isMultipleResultSetsEnabled() {
    return multipleResultSetsEnabled;
  }

  public void setMultipleResultSetsEnabled(boolean multipleResultSetsEnabled) {
    this.multipleResultSetsEnabled = multipleResultSetsEnabled;
  }

  public Set<String> getLazyLoadTriggerMethods() {
    return lazyLoadTriggerMethods;
  }

  public void setLazyLoadTriggerMethods(Set<String> lazyLoadTriggerMethods) {
    this.lazyLoadTriggerMethods = lazyLoadTriggerMethods;
  }

  public boolean isUseGeneratedKeys() {
    return useGeneratedKeys;
  }

  public void setUseGeneratedKeys(boolean useGeneratedKeys) {
    this.useGeneratedKeys = useGeneratedKeys;
  }

  public ExecutorType getDefaultExecutorType() {
    return defaultExecutorType;
  }

  public void setDefaultExecutorType(ExecutorType defaultExecutorType) {
    this.defaultExecutorType = defaultExecutorType;
  }

  public boolean isCacheEnabled() {
    return cacheEnabled;
  }

  public void setCacheEnabled(boolean cacheEnabled) {
    this.cacheEnabled = cacheEnabled;
  }

  public Integer getDefaultStatementTimeout() {
    return defaultStatementTimeout;
  }

  public void setDefaultStatementTimeout(Integer defaultStatementTimeout) {
    this.defaultStatementTimeout = defaultStatementTimeout;
  }

  /**
   * Gets the default fetch size.
   *
   * @return the default fetch size
   * @since 3.3.0
   */
  public Integer getDefaultFetchSize() {
    return defaultFetchSize;
  }

  /**
   * Sets the default fetch size.
   *
   * @param defaultFetchSize the new default fetch size
   * @since 3.3.0
   */
  public void setDefaultFetchSize(Integer defaultFetchSize) {
    this.defaultFetchSize = defaultFetchSize;
  }

  /**
   * Gets the default result set type.
   *
   * @return the default result set type
   * @since 3.5.2
   */
  public ResultSetType getDefaultResultSetType() {
    return defaultResultSetType;
  }

  /**
   * Sets the default result set type.
   *
   * @param defaultResultSetType the new default result set type
   * @since 3.5.2
   */
  public void setDefaultResultSetType(ResultSetType defaultResultSetType) {
    this.defaultResultSetType = defaultResultSetType;
  }

  public boolean isUseColumnLabel() {
    return useColumnLabel;
  }

  public void setUseColumnLabel(boolean useColumnLabel) {
    this.useColumnLabel = useColumnLabel;
  }

  public LocalCacheScope getLocalCacheScope() {
    return localCacheScope;
  }

  public void setLocalCacheScope(LocalCacheScope localCacheScope) {
    this.localCacheScope = localCacheScope;
  }

  public JdbcType getJdbcTypeForNull() {
    return jdbcTypeForNull;
  }

  public void setJdbcTypeForNull(JdbcType jdbcTypeForNull) {
    this.jdbcTypeForNull = jdbcTypeForNull;
  }

  public Properties getVariables() {
    return variables;
  }

  public void setVariables(Properties variables) {
    this.variables = variables;
  }

  public TypeHandlerRegistry getTypeHandlerRegistry() {
    return typeHandlerRegistry;
  }

  /**
   * Set a default {@link TypeHandler} class for {@link Enum}.
   * A default {@link TypeHandler} is {@link org.apache.ibatis.type.EnumTypeHandler}.
   *
   * @param typeHandler a type handler class for {@link Enum}
   * @since 3.4.5
   */
  public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
    if (typeHandler != null) {
      getTypeHandlerRegistry().setDefaultEnumTypeHandler(typeHandler);
    }
  }

  public TypeAliasRegistry getTypeAliasRegistry() {
    return typeAliasRegistry;
  }

  /**
   * Gets the mapper registry.
   *
   * @return the mapper registry
   * @since 3.2.2
   */
  public MapperRegistry getMapperRegistry() {
    return mapperRegistry;
  }

  public ReflectorFactory getReflectorFactory() {
    return reflectorFactory;
  }

  public void setReflectorFactory(ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
  }

  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  public void setObjectFactory(ObjectFactory objectFactory) {
    this.objectFactory = objectFactory;
  }

  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }

  public void setObjectWrapperFactory(ObjectWrapperFactory objectWrapperFactory) {
    this.objectWrapperFactory = objectWrapperFactory;
  }

  /**
   * Gets the interceptors.
   *
   * @return the interceptors
   * @since 3.2.2
   */
  public List<Interceptor> getInterceptors() {
    return interceptorChain.getInterceptors();
  }

  public LanguageDriverRegistry getLanguageRegistry() {
    return languageRegistry;
  }

  public void setDefaultScriptingLanguage(Class<? extends LanguageDriver> driver) {
    if (driver == null) {
      driver = XMLLanguageDriver.class;
    }
    getLanguageRegistry().setDefaultDriverClass(driver);
  }

  public LanguageDriver getDefaultScriptingLanguageInstance() {
    return languageRegistry.getDefaultDriver();
  }

  /**
   * 根据语言驱动器类型，获取语言驱动器：
   * 1、如果"语言驱动器类型"为null，则取得默认驱动
   * 2、"语言驱动器类型"不为null，则实例化对应的语言驱动器，并注册到"语言驱动器集合(LANGUAGE_DRIVER_MAP)"
   * 3、从"语言驱动器集合(LANGUAGE_DRIVER_MAP)"中，获取刚刚实例化、注册的语言驱动器
   *
   * Gets the language driver.
   *
   * @param langClass the lang class    语言驱动器类型
   * @return the language driver
   * @since 3.5.1
   */
  public LanguageDriver getLanguageDriver(Class<? extends LanguageDriver> langClass) {
    /*

    1、如果"语言驱动器类型"为null，则取得默认驱动（mybatis3.2以前大家一直用的方法）

     */
    if (langClass == null) {

      return languageRegistry.getDefaultDriver();
    }

    /*

    2、"语言驱动器类型"不为null，则实例话对应的语言驱动器，并注册到"语言驱动器集合(LANGUAGE_DRIVER_MAP)"
    （1）如果"语言驱动器集合"中不存在该类型的语言驱动器，则实例化对应类型的语言驱动器，并且存放"语言驱动器类型"和"语言驱动器实例"之间的对应关系
    （2）如果存在，则什么事情都不做

     */
    languageRegistry.register(langClass);

    /*

    3、从"语言驱动器集合(LANGUAGE_DRIVER_MAP)"中，获取刚刚实例化、注册的语言驱动器

     */
    return languageRegistry.getDriver(langClass);
  }

  /**
   * Gets the default scripting language instance.
   *
   * @return the default scripting language instance
   * @deprecated Use {@link #getDefaultScriptingLanguageInstance()}
   */
  @Deprecated
  public LanguageDriver getDefaultScriptingLanuageInstance() {
    return getDefaultScriptingLanguageInstance();
  }

  /**
   * 创建元对象
   */
  public MetaObject newMetaObject(Object object) {
    return MetaObject.forObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
  }

  /**
   * 创建ParameterHandler（参数处理器），并应用插件对其进行扩展
   */
  public ParameterHandler newParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    /* 1、创建ParameterHandler */
    ParameterHandler parameterHandler = mappedStatement.getLang().createParameterHandler(mappedStatement, parameterObject, boundSql);

    /* 2、应用插件对ParameterHandler进行扩展，其实就是对ParameterHandler进行动态代理！ */
    parameterHandler = (ParameterHandler) interceptorChain.pluginAll(parameterHandler);

    return parameterHandler;
  }

  /**
   * 创建ResultSetHandler（结果集处理器），并应用插件对其进行扩展
   *
   * @param executor
   * @param mappedStatement
   * @param rowBounds
   * @param parameterHandler
   * @param resultHandler
   * @param boundSql
   * @return
   */
  public ResultSetHandler newResultSetHandler(Executor executor, MappedStatement mappedStatement, RowBounds rowBounds, ParameterHandler parameterHandler,
                                              ResultHandler resultHandler, BoundSql boundSql) {

    /* 1、创建DefaultResultSetHandler */
    // 创建DefaultResultSetHandler(稍老一点的版本3.1是创建NestedResultSetHandler或者FastResultSetHandler)
    ResultSetHandler resultSetHandler = new DefaultResultSetHandler(executor, mappedStatement, parameterHandler, resultHandler, boundSql, rowBounds);

    /* 2、应用插件对ResultSetHandler进行扩展，其实就是对ResultSetHandler进行动态代理 */
    // 应用插件（也就是用一大堆拦截器来去对ResultSetHandler进行扩展！）
    // 题外：⚠️其实就是对ResultSetHandler进行动态代理
    resultSetHandler = (ResultSetHandler) interceptorChain.pluginAll(resultSetHandler);

    return resultSetHandler;
  }

  /**
   * 1、创建StatementHandler（语句处理器），里面：根据MappedStatement中配置的StatementType（默认是PreparedStatementHandler），创建对应的StatementHandler。
   * 在StatementHandler的构造器里面，最主要干了3件事：
   * （1）获取主键
   * （2）创建ParameterHandler（参数处理器），⚠️并应用插件对其进行扩展
   * （3）创建ResultSetHandler（结果集处理器），⚠️并应用插件对其进行扩展
   *
   * 2、应用插件对RoutingStatementHandler进行扩展，其实就是对RoutingStatementHandler进行动态代理
   *
   * @param executor
   * @param mappedStatement
   * @param parameterObject
   * @param rowBounds
   * @param resultHandler
   * @param boundSql
   * @return
   */
  public StatementHandler/* 语句处理器 */ newStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    /*

    1、创建RoutingStatementHandler，里面：根据MappedStatement中配置的StatementType（默认是PreparedStatementHandler），创建对应的StatementHandler。
    在StatementHandler的构造器里面，最主要干了3件事：
    （1）获取主键
    （2）创建ParameterHandler（参数处理器），⚠️并应用插件对其进行扩展
    （3）创建ResultSetHandler（结果集处理器），⚠️并应用插件对其进行扩展

    */
    StatementHandler statementHandler = new RoutingStatementHandler/* 路由选择语句处理器 */(executor, mappedStatement, parameterObject, rowBounds, resultHandler, boundSql);

    /*

    2、应用插件对RoutingStatementHandler进行扩展，其实就是对RoutingStatementHandler进行动态代理

    */
    // 插件在这里插入
    // 往statementHandler里面应用插件：用插件操作StatementHandler
    statementHandler = (StatementHandler) interceptorChain.pluginAll(statementHandler);
    return statementHandler;
  }

  public Executor newExecutor(Transaction transaction) {
    return newExecutor(transaction, defaultExecutorType);
  }

  /**
   * 根据执行器类型(executorType)，创建对应类型的执行器
   *
   * @param transaction  事务对象
   * @param executorType 执行器类型
   * @return
   */
  public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
    /* 1、确定执行类型 */

    executorType = executorType == null ? defaultExecutorType : executorType;
    Executor executor;

    /*

    2、根据执行器类型(executorType)，创建对应类型的执行器（执行器当中包含Configuration、transaction、一级缓存PerpetualCache）

    题外：里面最重要的是会创建一级缓存(PerpetualCache)！

    */
    // 然后就是简单的3个分支，产生3种执行器BatchExecutor/ReuseExecutor/SimpleExecutor
    if (ExecutorType.BATCH == executorType) {
      executor = new BatchExecutor(this, transaction);
    } else if (ExecutorType.REUSE == executorType) {
      executor = new ReuseExecutor(this, transaction);
    } else {
      executor = new SimpleExecutor(this, transaction);
    }

    /* 3、如果开启了二级缓存的功能(默认开启)，则改变当前的Executor对象为CachingExecutor */
    // 根据配置决定是否开启二级缓存的功能，默认开启。如果开启的话，会改变当前的执行器对象
    // 如果要求缓存，生成另一种CachingExecutor(默认就是有缓存),装饰者模式,所以默认都是返回CachingExecutor
    if (cacheEnabled) {
      /**
       * 只有在CachingExecutor中才有操作二级缓存的
       */
      executor = new CachingExecutor(executor);
    }

    /* 4、应用插件扩展Executor。其实也就是对Executor进行动态代理！ */
    // 题外：插件可以改变Executor(执行器)行为
    executor = (Executor) interceptorChain.pluginAll(executor);
    return executor;
  }

  public void addKeyGenerator(String id, KeyGenerator keyGenerator) {
    keyGenerators.put(id, keyGenerator);
  }

  public Collection<String> getKeyGeneratorNames() {
    return keyGenerators.keySet();
  }

  public Collection<KeyGenerator> getKeyGenerators() {
    return keyGenerators.values();
  }

  public KeyGenerator getKeyGenerator(String id) {
    return keyGenerators.get(id);
  }

  public boolean hasKeyGenerator(String id) {
    return keyGenerators.containsKey(id);
  }

  /**
   * 添加二级缓存
   *
   * @param cache 二级缓存，mapper级别
   */
  public void addCache(Cache cache) {
    // Configuration.StrictMap
    caches.put(cache.getId(), cache);
  }

  public Collection<String> getCacheNames() {
    return caches.keySet();
  }

  public Collection<Cache> getCaches() {
    return caches.values();
  }

  public Cache getCache(String id) {
    return caches.get(id);
  }

  public boolean hasCache(String id) {
    return caches.containsKey(id);
  }

  public void addResultMap(ResultMap rm) {
    resultMaps.put(rm.getId(), rm);
    checkLocallyForDiscriminatedNestedResultMaps/* 在本地检查有区别的嵌套结果映射 */(rm);
    checkGloballyForDiscriminatedNestedResultMaps/* 全局检查有区别的嵌套结果映射 */(rm);
  }

  public Collection<String> getResultMapNames() {
    return resultMaps.keySet();
  }

  public Collection<ResultMap> getResultMaps() {
    return resultMaps.values();
  }

  public ResultMap getResultMap(String id) {
    return resultMaps.get(id);
  }

  public boolean hasResultMap(String id) {
    return resultMaps.containsKey(id);
  }

  public void addParameterMap(ParameterMap pm) {
    parameterMaps.put(pm.getId(), pm);
  }

  public Collection<String> getParameterMapNames() {
    return parameterMaps.keySet();
  }

  public Collection<ParameterMap> getParameterMaps() {
    return parameterMaps.values();
  }

  public ParameterMap getParameterMap(String id) {
    return parameterMaps.get(id);
  }

  public boolean hasParameterMap(String id) {
    return parameterMaps.containsKey(id);
  }

  public void addMappedStatement(MappedStatement ms) {
    mappedStatements.put(ms.getId(), ms);
  }

  /**
   * (1)解析剩下的之前解析出错的ResultMap、引用缓存、sql标签、sql注解（sql标签和sql注解是构建MappedStatement，放入Configuration.mappedStatements）
   * (2)获取所有的Configuration.mappedStatements map的key值
   */
  public Collection<String> getMappedStatementNames() {
    /*

    1、解析剩下的之前解析出错的ResultMap、引用缓存、sql标签、sql注解（sql标签和sql注解是构建MappedStatement，放入Configuration.mappedStatements）
      (1)解析剩下的之前解析出错的ResultMap
      (2)解析剩下的之前解析出错的引用缓存
      (3)解析剩下的之前解析出错的<select><insert><update><delete>标签，构建MappedStatement对象，放入Configuration.mappedStatements
      (4)解析剩下的之前解析出错的@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider，构建MappedStatement对象，放入Configuration.mappedStatements

     */
    buildAllStatements();

    /* 2、获取所有的Configuration.mappedStatements map的key值 */
    return mappedStatements.keySet();
  }

  public Collection<MappedStatement> getMappedStatements() {
    buildAllStatements();
    return mappedStatements.values();
  }

  public Collection<XMLStatementBuilder> getIncompleteStatements() {
    return incompleteStatements;
  }

  public void addIncompleteStatement(XMLStatementBuilder incompleteStatement) {
    incompleteStatements.add(incompleteStatement);
  }

  public Collection<CacheRefResolver> getIncompleteCacheRefs() {
    return incompleteCacheRefs;
  }

  public void addIncompleteCacheRef(CacheRefResolver incompleteCacheRef) {
    incompleteCacheRefs.add(incompleteCacheRef);
  }

  public Collection<ResultMapResolver> getIncompleteResultMaps() {
    return incompleteResultMaps;
  }

  public void addIncompleteResultMap(ResultMapResolver resultMapResolver) {
    incompleteResultMaps.add(resultMapResolver);
  }

  public void addIncompleteMethod(MethodResolver builder) {
    incompleteMethods.add(builder);
  }

  public Collection<MethodResolver> getIncompleteMethods() {
    return incompleteMethods;
  }

  // 由DefaultSqlSession.selectList()调用过来
  public MappedStatement getMappedStatement(String id) {
    return this.getMappedStatement(id, true);
  }

  public MappedStatement getMappedStatement(String id, boolean validateIncompleteStatements) {
    // 先构建所有语句，再返回语句
    if (validateIncompleteStatements) {
      buildAllStatements();
    }
    return mappedStatements.get(id);
  }

  public Map<String, XNode> getSqlFragments() {
    return sqlFragments;
  }

  public void addInterceptor(Interceptor interceptor) {
    interceptorChain.addInterceptor(interceptor);
  }

  // 将包下所有类加入到mapper()
  public void addMappers(String packageName, Class<?> superType) {
    mapperRegistry.addMappers(packageName, superType);
  }

  /**
   * 扫描注册指定包下所有的映射器
   * 1、获取包下所有的Object类型的类，由于接口也是继承Object，所以会被识别到
   * 2、然后会判断，只有是接口，才会进行解析和注册
   * 3、注册mapper（映射器）流程：
   * 3.1、先是构建mapper接口对应的MapperProxyFactory，用于生成mapper接口的代理对象；并将它两的对应关系，存入knownMappers（已知mapper）集合中
   * 3.2、然后去解析mapper，分为2部分：
   * （1）先解析mapper接口对应的dao.xml文件，将对应信息放入Configuration；—— 配置文件开发
   * （2）然后再解析mapper接口（把mapper接口作为映射文件进行解析），将对应信息放入Configuration—— 注解开发
   *
   * @param packageName   包名称
   */
  public void addMappers(String packageName) {
    mapperRegistry.addMappers(packageName);
  }

  public <T> void addMapper(Class<T> type) {
    mapperRegistry.addMapper(type);
  }

  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    return mapperRegistry.getMapper(type, sqlSession);
  }

  public boolean hasMapper(Class<?> type) {
    return mapperRegistry.hasMapper(type);
  }

  public boolean hasStatement(String statementName) {
    return hasStatement(statementName, true);
  }

  public boolean hasStatement(String statementName, boolean validateIncompleteStatements) {
    if (validateIncompleteStatements) {
      buildAllStatements();
    }
    return mappedStatements.containsKey(statementName);
  }

  public void addCacheRef(String namespace, String referencedNamespace) {
    cacheRefMap.put(namespace, referencedNamespace);
  }


  /**
   * 1、解析剩下的之前解析出错的ResultMap、引用缓存、sql标签、sql注解（sql标签和sql注解是构建MappedStatement，放入Configuration.mappedStatements）
   * (1)解析剩下的之前解析出错的ResultMap
   * (2)解析剩下的之前解析出错的引用缓存
   * (3)解析剩下的之前解析出错的<select><insert><update><delete>标签，构建MappedStatement对象，放入Configuration.mappedStatements
   * (4)解析剩下的之前解析出错的@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider，构建MappedStatement对象，放入Configuration.mappedStatements
   */
  /*
   * Parses all the unprocessed statement nodes in the cache. It is recommended
   * to call this method once all the mappers are added as it provides fail-fast
   * statement validation. —— 解析缓存中所有未处理的语句节点。建议在添加所有映射器后调用此方法，因为它提供了快速失败的语句验证。
   */
  protected void buildAllStatements() {
    /* 1、解析剩下的之前解析出错的ResultMap */
    // 解析剩下的出错的ResultMap：之前在构建ResultMap时，出错IncompleteElementException的ResultMap，现在重新构建一下
    parsePendingResultMaps/* 解析待处理的结果映射 */();

    /* 2、解析剩下的之前解析出错的引用缓存 */
    if (!incompleteCacheRefs.isEmpty()) {
      synchronized (incompleteCacheRefs) {
        // 从configuration.caches中，通过引用缓存的命名空间，去获取对应命名空间的缓存，作为引用缓存；
        // 如果不存在，就报错
        incompleteCacheRefs.removeIf(x -> x.resolveCacheRef() != null);
      }
    }

    /* 3、解析剩下的之前解析出错的<select><insert><update><delete>标签，构建MappedStatement对象，放入Configuration.mappedStatements */
    if (!incompleteStatements.isEmpty()) {
      synchronized (incompleteStatements) {
        incompleteStatements.removeIf(x -> {
          // 解析<select><insert><update><delete>标签信息，
          // 然后用标签里面的信息，构建MappedStatement对象，放入到Configuration.mappedStatements里面去
          x.parseStatementNode();
          return true;
        });
      }
    }

    /* 4、解析剩下的之前解析出错的@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider，构建MappedStatement对象，放入Configuration.mappedStatements */
    if (!incompleteMethods.isEmpty()) {
      synchronized (incompleteMethods) {
        incompleteMethods.removeIf(x -> {
          // 解析方法上的@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider注解，
          // 然后用注解里面的信息，构建MappedStatement对象，放入到Configuration.mappedStatements里面去
          x.resolve();
          return true;
        });
      }
    }
  }

  /**
   * 解析剩下的出错的ResultMap：之前在构建ResultMap时，出错IncompleteElementException的ResultMap，现在重新构建一下
   */
  private void parsePendingResultMaps() {
    if (incompleteResultMaps.isEmpty()) {
      return;
    }
    synchronized (incompleteResultMaps) {
      boolean resolved;
      IncompleteElementException ex = null;
      do {
        resolved = false;
        Iterator<ResultMapResolver> iterator = incompleteResultMaps.iterator();
        while (iterator.hasNext()) {
          try {
            // ⚠️构建ResultMap对象，并添加ResultMap(结果映射)到configuration.resultMaps中
            iterator.next().resolve();
            iterator.remove();
            resolved = true;
          } catch (IncompleteElementException e) {
            ex = e;
          }
        }
      } while (resolved);
      if (!incompleteResultMaps.isEmpty() && ex != null) {
        // At least one result map is unresolvable.
        throw ex;
      }
    }
  }

  /**
   * Extracts namespace from fully qualified statement id.
   *
   * @param statementId the statement id
   * @return namespace or null when id does not contain period.
   */
  protected String extractNamespace(String statementId) {
    int lastPeriod = statementId.lastIndexOf('.');
    return lastPeriod > 0 ? statementId.substring(0, lastPeriod) : null;
  }

  // Slow but a one time cost. A better solution is welcome.
  protected void checkGloballyForDiscriminatedNestedResultMaps(ResultMap rm) {
    if (rm.hasNestedResultMaps()) {
      for (Map.Entry<String, ResultMap> entry : resultMaps.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof ResultMap) {
          ResultMap entryResultMap = (ResultMap) value;
          if (!entryResultMap.hasNestedResultMaps() && entryResultMap.getDiscriminator() != null) {
            Collection<String> discriminatedResultMapNames = entryResultMap.getDiscriminator().getDiscriminatorMap().values();
            if (discriminatedResultMapNames.contains(rm.getId())) {
              entryResultMap.forceNestedResultMaps();
            }
          }
        }
      }
    }
  }

  // Slow but a one time cost. A better solution is welcome.
  protected void checkLocallyForDiscriminatedNestedResultMaps(ResultMap rm) {
    if (!rm.hasNestedResultMaps() && rm.getDiscriminator() != null) {
      for (Map.Entry<String, String> entry : rm.getDiscriminator().getDiscriminatorMap().entrySet()) {
        String discriminatedResultMapName = entry.getValue();
        if (hasResultMap(discriminatedResultMapName)) {
          ResultMap discriminatedResultMap = resultMaps.get(discriminatedResultMapName);
          if (discriminatedResultMap.hasNestedResultMaps()) {
            rm.forceNestedResultMaps();
            break;
          }
        }
      }
    }
  }

  // 静态内部类,严格的Map，不允许多次覆盖key所对应的value
  protected static class StrictMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -4950446264854982944L;
    private final String name;
    private BiFunction<V, V, String> conflictMessageProducer;

    public StrictMap(String name, int initialCapacity, float loadFactor) {
      super(initialCapacity, loadFactor);
      this.name = name;
    }

    public StrictMap(String name, int initialCapacity) {
      super(initialCapacity);
      this.name = name;
    }

    public StrictMap(String name) {
      super();
      this.name = name;
    }

    public StrictMap(String name, Map<String, ? extends V> m) {
      super(m);
      this.name = name;
    }

    /**
     * Assign a function for producing a conflict error message when contains value with the same key.
     * <p>
     * function arguments are 1st is saved value and 2nd is target value.
     *
     * @param conflictMessageProducer A function for producing a conflict error message
     * @return a conflict error message
     * @since 3.5.0
     */
    public StrictMap<V> conflictMessageProducer(BiFunction<V, V, String> conflictMessageProducer) {
      this.conflictMessageProducer = conflictMessageProducer;
      return this;
    }


    /**
     * 存放数据
     *
     * 注意：⚠️如果有包名，会放2个key到这个map，一个缩略，一个全名
     */
    @Override
    @SuppressWarnings("unchecked")
    public V put(String key, V value) {
      /* 1、如果已经存在此key了，则直接报错 */
      if (containsKey(key)) {
        // 如果已经存在此key了，直接报错
        throw new IllegalArgumentException(name + " already contains value for " + key
          + (conflictMessageProducer == null ? "" : conflictMessageProducer.apply(super.get(key), value)));
      }

      /* 2、保存短名称key */
      if (key.contains(".")) {

        // 获取短名称

        // 如果有.符号，取得短名称，大致用意就是包名不同，类名相同，提供模糊查询的功能
        // 例如：key=com.hm.m_04.dao.UserDao，获取后shortKey=UserDao，而不是全限定类名
        final String shortKey = getShortName(key);
        /* 2、不存在短名称key的value值，则保存短名称key和value值 */
        if (super.get(shortKey) == null) {
          // 如果没有这个缩略，则放一个缩略
          super.put(shortKey, value);
        }
        /* 3、存在短名称key的value值，则保存短名称key和Ambiguity */
        else {
          // 如果已经有此缩略，表示模糊，放一个Ambiguity型的
          super.put(shortKey, (V) new Ambiguity/* 有歧义的 */(shortKey));
        }
      }

      /* 3、保存全名 */
      // 再放一个全名
      return super.put(key, value);

      // 题外：⚠️可以看到，如果有包名，会放2个key到这个map，一个缩略，一个全名

    }

    @Override
    public V get(Object key) {
      V value = super.get(key);
      // 如果找不到相应的key，直接报错
      if (value == null) {
        throw new IllegalArgumentException(name + " does not contain value for " + key);
      }
      // 如果是模糊型的，也报错，提示用户
      // 原来这个模糊型就是为了提示用户啊
      if (value instanceof Ambiguity) {
        throw new IllegalArgumentException(((Ambiguity) value).getSubject() + " is ambiguous in " + name
          + " (try using the full name including the namespace, or rename one of the entries)");
      }
      return value;
    }

    // 模糊，居然放在Map里面的一个静态内部类
    protected static class Ambiguity/* 有歧义的 */ {

      // 短名称key，例如：UserDao，而不是全限定类名
      private final String subject;

      public Ambiguity(String subject) {
        this.subject = subject;
      }

      public String getSubject() {
        return subject;
      }

    }

    // 取得短名称，也就是取得最后那个句号的后面那部分
    private String getShortName(String key) {
      final String[] keyParts = key.split("\\.");
      return keyParts[keyParts.length - 1];
    }
  }

}
