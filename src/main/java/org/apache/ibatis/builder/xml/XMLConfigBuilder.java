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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * XML配置构建器（建造者模式，继承BaseBuilder）
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  // 标识是否己经解析过mybatis-config.xmL配置文件
  // true：已解析，false：未解析
  private boolean parsed;
  /**
   * mybatis配置文件解析器，作用：从配置文件根节点<configuration>，开始进行解析，得到一个父子树状结构的XNode对象，有了这个对象，就可以对数据进行操作！
   * 注意：这个对象的作用，并不是具体提取属性值，然后对其进行处理；只是构建树状结构的父子Node节点
   */
  // mybatis配置文件解析器（用于解析mybatis-config.xml配置文件的XPathParser对象）
  private final XPathParser parser;
  // 标识<environment>配置的名称
  private String environment;
  // ReflectorFactory负责创建和缓存Reflector对象
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();


  /* 以下3个一组 */

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  // 构造函数，转换成XPathParser再去调用构造函数
  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    // 构造一个需要验证，XMLMapperEntityResolver的XPathParser
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  /* 以下3个一组 */

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  /**
   *
   * @param inputStream       mybatis配置文件的输入流
   * @param environment       外部传入的环境名称
   * @param props             外部传入的属性
   */
  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    /* 1、new XPathParser()：里面，解析mybatis配置文件为一个document对象 */
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }


  // 上面的6个构造函数最后都会合流到这个函数，传入XPathParser
  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    /* 1、new Configuration()：创建Configuration对象 */
    /* 2、super()：将Configuration对象保存在当前XMLConfigBuilder对象里面，以及获取Configuration中的类型别名注册器、类型处理器注册器，放入到当前XMLConfigBuilder对象里面 */
    // 首先创建Configuration，并保存在父类当中
    super(/* ⚠️ */new Configuration());

    // 错误上下文设置成SQL Mapper Configuration(XML文件配置),以便后面出错了报错用吧
    ErrorContext.instance().resource("SQL Mapper Configuration");

    // 设置外部传入的属性到Configuration中去
    this.configuration.setVariables(props);

    // 当前mybatis配置文件是否已经解析的标识（true：已解析；false：未解析）
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   * 解析配置文件
   */
  public Configuration parse() {
    /*

    1、判断mybatis配置文件是否已经解析过了
    如果已经解析过了，报错，也就是说每个配置文件只能被加载一次

     */
    if (parsed) {
      // 每个XMLConfigBuilder只能使用一次
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }

    /* 2、如果没解析，就进行解析 */

    /* 3、设置"mybatis配置文件是否解析的标识"为true，代表已经解析了，因为接下来就是要进行解析了 */
    parsed = true;

    /* 4、解析mybatis配置文件 */
    /**
     * 1、parser.evalNode("/configuration")：获取配置文件的根节点（/configuration节点），用于解析！
     */
    // 获取配置文件的根节点（/configuration节点），从根节点开始解析mybatis配置文件
    // 题外：这里解析Configuration：指的是mybatis配置文件里面最外层的<configuration>标签
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 解析配置文件中的信息，填充到Configuration对象里面去
   */
  private void parseConfiguration(XNode root) {
    try {
      // issue #117 read properties first

      /* 1、读取属性配置 */
      // 1、解析<properties>标签
      // >>> 也就是：读取属性配置：有<properties>标签内配置的属性；也有<properties>标签所指定的外部配置文件(.properties)中的属性，然后放入到Configuration
      propertiesElement(root.evalNode("properties"));

      /* 2、读取mybatis设置信息 */
      // 2、解析<settings>标签
      // >>> 也就是：读取mybatis设置信息，对应Configuration里面的字段
      // >>> 题外：这些是极其重要的调整，它们会修改MyBatis在运行时的行为方式
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      // 设置vfsImpl字段
      loadCustomVfs(settings);
      // 显式定义用什么log框架，不定义则用默认的自动发现jar包机制
      loadCustomLogImpl(settings);

      /* 3、注册别名 */
      // 3、解析<typeAliases>标签
      // >>> 也就是：注册类型别名：有直接注册配置的别名和类型；也有扫描指定包下的类，注册别名(有@Alias注解则用，没有则取类的simpleName)，然后放入到Configuration
      typeAliasesElement(root.evalNode("typeAliases"));

      /* 4、注册插件（注意：⚠️这里只是注册，并未应用） */
      /**
       * mybatis的插件，例如：分页插件
       */
      // 4、解析<plugins>标签
      // >>> 也就是：注册插件：实例化插件(插件其实就是一个拦截器)，放入到Configuration
      pluginElement(root.evalNode("plugins"));

      /* 5、注册对象工厂 */
      /**
       * 对象工厂：用于自定义对象创建的方式,比如用对象池？
       */
      // 5、解析<objectFactory>标签
      // >>> 也就是：注册对象工厂：实例化对象工厂，放入到Configuration中
      objectFactoryElement(root.evalNode("objectFactory"));

      /* 6、注册对象包裝工厂 */
      // 6、解析<objectWrapperFactory>标签
      // >>> 也就是：注册对象包裝工厂：实例化对象包裝工厂，放入到Configuration中
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));

      /* 7、注册反射工厂 */
      // 7、解析<reflectorFactory>标签
      // >>> 也就是：注册反射工厂：实例化反射工厂，放入到Configuration中
      reflectorFactoryElement(root.evalNode("reflectorFactory"));

      // 在前面，解析好了设置的属性值；现在把那些属性值，设置到configuration对象
      // 设置具体的属性到configuration对象
      settingsElement(settings);

      /* 8、注册数据库环境(Environment，包含：数据库id、事务工厂、数据源) */
      // read it after objectFactory and objectWrapperFactory issue #631 —— 在 objectFactory 和 objectWrapperFactory 问题 631 之后阅读它
      // 8、解析<environments>标签
      // >>> 也就是：注册数据库环境(Environment)：获取数据库环境信息（数据库id、事务工厂、数据源）构建成一个Environment对象，放入到Configuration
      // 题外：Environment里面有2个非常重要的对象：TransactionFactory、dataSource
      environmentsElement(root.evalNode("environments"));

      /* 9、确定数据库id */
      // 9、解析<databaseIdProvider>标签
      // >>> 也就是确定数据库id，设置到configuration对象
      // 解析对应的数据库厂商
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));

      /* 10、注册类型处理器 */
      // 10、解析<typeHandlers>标签
      // >>> 也就是：注册类型处理器到Configuration
      typeHandlerElement(root.evalNode("typeHandlers"));

      /* 11、注册映射器（也就是解析所有mapper文件，有可能是纯接口，也有可能是dao.xml文件） */
      /**
       * 题外：mapper可以指定为接口，也可以指定为配置文件！
       * 题外：mapper：里面存放了sql语句
       */
      // 11、解析<mappers>标签
      // >>> 也就是：注册映射器到Configuration
      mapperElement(root.evalNode("mappers"));

      // 至此，解析完毕了所有的配置。当配置解析完成了，所有的准备工作也都完成了

    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  /**
   * 设置
   *
   * 解析<settings>标签
   * >>> 也就是：mybatis设置，对应Configuration里面的字段
   * >>> 题外：这些是极其重要的调整，它们会修改MyBatis在运行时的行为方式
   *
   * <settings>
   *   <setting name="cacheEnabled" value="true"/>
   *   <setting name="lazyLoadingEnabled" value="true"/>
   *   <setting name="multipleResultSetsEnabled" value="true"/>
   *   <setting name="useColumnLabel" value="true"/>
   *   <setting name="useGeneratedKeys" value="false"/>
   *   <setting name="enhancementEnabled" value="false"/>
   *   <setting name="defaultExecutorType" value="SIMPLE"/>
   *   <setting name="defaultStatementTimeout" value="25000"/>
   *   <setting name="safeRowBoundsEnabled" value="false"/>
   *   <setting name="mapUnderscoreToCamelCase" value="false"/>
   *   <setting name="localCacheScope" value="SESSION"/>
   *   <setting name="jdbcTypeForNull" value="OTHER"/>
   *   <setting name="lazyLoadTriggerMethods" value="equals,clone,hashCode,toString"/>
   * </settings>
   */
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    /* 1、获取<settings>下的子标签<setting>中的name和value所设置的属性值，形成一个Properties */
    // 获取<settings>下的子标签<setting>中的name和value所设置的属性值，形成一个Properties
    Properties props = context.getChildrenAsProperties();

    /* 2、检查我们配置的属性，是存在于Configuration类当中的字段，确保我们配置的属性没有拼写错误 */
    // Check that all settings are known to the configuration class —— 检查配置类是否知道所有设置
    // 检查下是否在Configuration类里都有相应的setter方法（没有拼写错误）
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      // 判断我们<setting>标签配置的name属性值，是否是Configuration这个类中的字段
      // 如果不是，就报错！也就是说<setting>标签的name属性值，只能是Configuration类中的字段
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        // 设置“+键+”未知。确保拼写正确（区分大小写）。
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    // 显式定义用什么log框架，不定义则用默认的自动发现jar包机制
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  /**
   * 解析<typeAliases>标签，也就是注册类型别名
   *
   * 参考：
   * <typeAliases>
   *    <typeAlias alias="Author" type="domain.blog.Author"/>
   *    <typeAlias alias="Blog" type="domain.blog.Blog"/>
   *    <typeAlias alias="Comment" type="domain.blog.Comment"/>
   *    <typeAlias alias="Post" type="domain.blog.Post"/>
   *    <typeAlias alias="Section" type="domain.blog.Section"/>
   *    <typeAlias alias="Tag" type="domain.blog.Tag"/>
   * </typeAliases>
   * or
   * <typeAliases>
   *    <package name="domain.blog"/>
   * </typeAliases>
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      // 处理全部子标签
      for (XNode child : parent.getChildren()) {
        /* 1、扫描指定包中的所有类，注册别名：有@Alias注解则用，没有则取类的simpleName */
        // 处理<package>标签
        if ("package".equals(child.getName())) {
          // 获取指定的包名
          String typeAliasPackage = child.getStringAttribute("name");
          // 通过TypeAliasRegistry扫描指定包中所有的类，并解析@Alias注解，完成别名注册（有@Alias注解则用，没有则取类的simpleName）
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        }
        /* 2、直接注册配置的别名和类型 */
        // 处理<typeAlias>标签
        else {
          // 获取指定的别名
          String alias = child.getStringAttribute("alias");
          // 获取别名对应的类型
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            // 根据Class名字来注册类型别名
            if (alias == null) {
              // 扫描@Alias注解，完成注册
              typeAliasRegistry.registerAlias(clazz);
            }
            // 注册别名
            else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }


  /**
   * 解析<plugins>标签，也就是实例化插件(插件其实就是一个拦截器)，放入到Configuration
   *
   * 题外：MyBatis允许你在某一点，拦截已映射语句执行的调用 —— 默认情况下，MyBatis允许使用插件来拦截方法调用。
   *
   * 参考：
   * <plugins>
   *   <plugin interceptor="org.mybatis.example.ExamplePlugin">
   *     <property name="someProperty" value="100"/>
   *   </plugin>
   * </plugins>
   */
  private void pluginElement(XNode parent) throws Exception {
    /* 1、实例化所有插件(插件其实就是一个拦截器)，放入到Configuration */
    if (parent != null) {
      // 遍历<plugins>下的全部<plugin>子标签
      for (XNode child/* <plugin> */ : parent.getChildren()) {

        // 获取<plugin>标签的interceptor属性
        String interceptor = child.getStringAttribute("interceptor");
        // 获取<plugin>标签下的<property>标签配置的信息，形成properties对象
        // 题外：这个属性是作为插件的属性
        Properties properties = child.getChildrenAsProperties();

        // 实例化插件（实例化Interceptor对象）
        // 题外：通过Interceptor的方式来集成插件
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
        // 设置插件的属性（设置Interceptor的属性），也就是<plugin>标签下的<property>标签配置的信息
        interceptorInstance.setProperties(properties);

        // 往configuration.interceptorChain.interceptors里面添加Interceptor
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   * 解析<objectFactory>标签，也就是实例化对象工厂，放入到Configuration中
   *
   * 对象工厂：用于自定义对象创建的方式,比如用对象池？
   *
   * 参考：
   * <objectFactory type="org.mybatis.example.ExampleObjectFactory">
   *   <property name="someProperty" value="100"/>
   * </objectFactory>
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取配置的对象工厂全限定类名
      String type = context.getStringAttribute("type");
      // 获取<objectFactory>标签下的所有<property>标签配置的属性值，形成一个Properties
      Properties properties = context.getChildrenAsProperties();
      // 实例化对象工厂
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 往对象工厂当中设置配置的属性值
      factory.setProperties(properties);
      // 把对象工厂，放入到Configuration中
      configuration.setObjectFactory(factory);
    }
  }

  /**
   * 解析<objectWrapperFactory>标签
   * >>> 也就是：注册对象包裝工厂：实例化对象包裝工厂，放入到Configuration中
   *
   * 参考：
   * <objectWrapperFactory type="com.msb.other.objectWrapperFactory.MapWrapperFactory"/>
   * 注意：这个标签的放置位置，具体查看dtd文件，例如，这里不能放置到<environments>标签下面，否则会报错，也不能放置到<settings>上面，也会报错！
   */
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取配置的对象包装工厂类的全限定名
      String type = context.getStringAttribute("type");
      // 实例化对象包装工厂
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 注册对象包装工厂到configuration
      configuration.setObjectWrapperFactory(factory);
    }
  }

  /**
   * 解析<reflectorFactory>标签
   * >>> 也就是：注册反射工厂：实例化反射工厂，放入到Configuration中
   *
   * 参考：
   * <reflectorFactory type=""/>
   *
   * @param context
   * @throws Exception
   */
  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取反射工厂类型
      String type = context.getStringAttribute("type");
      // 实例化反射工厂
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 放入到Configuration中
      configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 解析<properties>标签，也就是读取属性配置：有<properties>标签内配置的属性，也有<properties>标签所指定的外部配置文件(.properties)中的属性
   *
   * 参考：
   * <configuration>
   *      <properties  resource="db.properties" url="">
   *        <property name="username" value="dev_user"/>
   *        <property name="password" value="F2Fa3!33TYyg"/>
   *      </properties>
   * </configuration>
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      /*

      1、先读取<properties>标签内配置的属性，形成一个Properties对象

      题外：也就是读取<properties>标签中的子标签<property>中配置的属性值，然后形成一个Properties对象

      */
      Properties defaults = context.getChildrenAsProperties();

      /*

      2、读取<properties>标签所指定的外部配置文件(.properties)中的属性

      题外：也就是获取<properties>标签配置的resource属性所指向的.properties文件中的属性值；或者是url属性所指向的.properties文件中的属性值
      注意：这里面的属性值会覆盖上面的属性值

      */
      // 解析properties的resource和Url属性，这两个属性用于确定properties配置文件的位置
      String resource = context.getStringAttribute("resource");
      // 题外：url：表示一个地址
      String url = context.getStringAttribute("url");

      // 如果同时指定了resource和url属性，则报错
      if (resource != null && url != null) {
        // properties元素不能同时指定URL和基于资源的属性文件引用。请指定其中之一。
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      // 读取resource属性值所指定的【.properties文件】内容，形成Properties
      if (resource != null) {
        // 注意：这会覆盖上面的值
        defaults.putAll(Resources.getResourceAsProperties(resource));
      }
      // 读取url属性值所指定的【.properties文件】内容，形成Properties
      else if (url != null) {
        // 注意：这会覆盖上面的值
        defaults.putAll(Resources.getUrlAsProperties(url));
      }

      /*

      3、读取Configuration中的属性值（Properties）

      题外：Configuration中的属性值，来源是通过调用构造函数时传入的：public XMLConfigBuilder(Reader reader, String environment, Properties props){}
      注意：这里面的属性值会覆盖上面的属性值，所以这里的优先级是最高的！

      */
      Properties vars = configuration.getVariables();
      if (vars != null) {
        // 注意：这会覆盖上面的值
        defaults.putAll(vars);
      }

      /* 4、将读取到的属性值（Properties），放入到XPathParser和Configuration中 */

      /**
       * 1、疑问：为什么defaults保存在了configuration中，又要保存在parser中呢？
       * 因为这些引入的变量，有可能在后续的解析过程中，需要用到，进行赋值的，所以要把defaults保存在XPathParser中。
       * 例如：数据库的4个连接属性，在后续解析<environments>标签的时候，其中${url}是需要用defaults里面的变量进行赋值的，
       * 所以要把defaults保存在parser中，后面在解析数据源的时候，才可以把数据库连接属性放进去！
       * <configuration>
       *     <properties resource="db.properties"/>
       *     <environments default="development">
       *         <environment id="development">
       *             <transactionManager type="JDBC"/>
       *             <dataSource type="POOLED">
       *                 <property name="driver" value="${driver}"/>
       *                 <property name="url" value="${url}"/>
       *                 <property name="username" value="${username}"/>
       *                 <property name="password" value="${password}"/>
       *             </dataSource>
       *         </environment>
       *     </environments>
       * </configuration>
       */
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(Properties props) {
    /* 在前面，解析好了设置的属性值；现在把那些属性值，设置到configuration对象 */

    // 如何自动映射列到字段/属性
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));

    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    // 是否允许缓存操作
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    // proxyFactory (CGLIB | JAVASSIST)
    // 延迟加载的核心技术就是用代理模式，CGLIB/JAVASSIST两者选一
    // 设置代理工厂是啥
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    // 延迟加载（懒加载）
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    // 延迟加载时，每种属性是否还要按需加载
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    // 允不允许多种结果集从一个单独 的语句中返回
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    // 使用列标签代替列名
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    // 允许JDBC支持生成的键
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    // 配置默认的执行器
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    // 超时时间
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    // 是否将DB字段自动映射到驼峰式Java属性（A_COLUMN-->aColumn）
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    // 嵌套语句上使用RowBounds
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    // 默认用session级别的缓存
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    // 为null值设置jdbcType
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    // Object的哪些方法将触发延迟加载
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"/* 默认值 */));
    // 使用安全的ResultHandler
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    // 动态SQL生成语言所使用的脚本语言
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    // 当结果集中含有Null值时是否执行映射对象的setter或者Map对象的put方法。此设置对于原始类型如int,boolean等无效。
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    // logger名字的前缀
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    // 配置工厂
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
    configuration.setArgNameBasedConstructorAutoMapping(booleanValueOf(props.getProperty("argNameBasedConstructorAutoMapping"), false));
    configuration.setDefaultSqlProviderType(resolveClass(props.getProperty("defaultSqlProviderType")));
    configuration.setNullableOnForEach(booleanValueOf(props.getProperty("nullableOnForEach"), false));
  }

  /**
   * 数据库环境
   *
   * 解析<environments>标签
   * >>> 也就是：注册数据库环境(Environment)：获取数据库环境信息（数据库id、事务工厂、数据源）构建成一个Environment对象，放入到Configuration
   *
   * 完整参考：
   * <environments default="development">
   *   <environment id="development">
   *     <transactionManager type="JDBC">
   *       <property name="..." value="..."/>
   *     </transactionManager>
   *     <dataSource type="POOLED">
   *       <property name="driver" value="${driver}"/>
   *       <property name="url" value="${url}"/>
   *       <property name="username" value="${username}"/>
   *       <property name="password" value="${password}"/>
   *     </dataSource>
   *   </environment>
   * </environments>
   *
   * 正常参考：
   *   <environments default="development">
   *     <environment id="development">
   *       <transactionManager type="JDBC"/>
   *       <dataSource type="POOLED">
   *         <property name="driver" value="${jdbc.driver}"/>
   *         <property name="url" value="${jdbc.url}"/>
   *         <property name="username" value="${jdbc.username}"/>
   *         <property name="password" value="${jdbc.password}"/>
   *       </dataSource>
   *     </environment>
   *   </environments>
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      /* 1、获取默认使用的环境 */
      if (environment == null) {
        // 获取默认使用的环境
        environment = context.getStringAttribute("default");
      }
      /**
       * 参考：
       * <environment id="development">
       *   <transactionManager type="JDBC"/>
       *   <dataSource type="POOLED">
       *     <property name="driver" value="${jdbc.driver}"/>
       *     <property name="url" value="${jdbc.url}"/>
       *     <property name="username" value="${jdbc.username}"/>
       *     <property name="password" value="${jdbc.password}"/>
       *   </dataSource>
       * </environment>
       */
      // 遍历<environments>中的子标签：<environment>
      for (XNode child/* <environment> */ : context.getChildren()) {
        // 获取<environment id="development">中的id属性
        String id = child.getStringAttribute("id");

        /* 2、判断是不是开发环境，只加载开发环境的数据库信息 */
        // 判断当前环境是不是开发环境(判断<environment>标签中的id是不是development)，只有是开发环境(id="development")，才会进行加载配置的数据库信息，也就是说，只会加载开发环境的数据库信息
        // 题外：由于只加载开发环境的数据库信息，所以Environment.id一定等于development
        if (isSpecifiedEnvironment(id)) {

          /* 3、实例化事务工厂（事务管理器） */
          // 解析<transactionManager type="JDBC"/>标签，也就是实例化事务管理器
          // 题外：后面可以根据TransactionFactory，构建出一个JdbcTransactionManager这个对象，来管理整个sql执行过程中的事务
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));

          /* 4、实例化数据源工厂 */
          // 解析<dataSource>标签，得出数据源工厂
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));

          /* 5、通过数据源工厂，获取数据源 */
          // 题外：此处获取数据源，并不会进行数据库连接
          DataSource dataSource = dsFactory.getDataSource();

          /*

          6、通过数据库环境id、事务工厂、数据源、构建出一个数据库环境对象（Environment）

          其实就是创建Environment，然后把"数据库环境id、事务工厂、数据源"保存在其中

          */
          // 题外：Environment里面有2个非常重要的对象：TransactionFactory、dataSource
          // 题外：由于只加载开发环境的数据库信息，所以id一定等于development
          Environment.Builder environmentBuilder = new Environment.Builder(id)
            .transactionFactory(txFactory)
            .dataSource(dataSource);

          /* 7、将环境对象（Environment），放入到Configuration */
          configuration.setEnvironment(environmentBuilder.build());
          break;
        }
      }
    }
  }

  /**
   * 解析<databaseIdProvider>标签，也就是确定数据库id
   *
   * 可以根据不同数据库执行不同的SQL，sql要加databaseId属性
   * 这个功能感觉不是很实用，真要多数据库支持，那SQL工作量将会成倍增长，用mybatis以后一般就绑死在一个数据库上了。但也是一个不得已的方法吧
   * 可以参考org.apache.ibatis.submitted.multidb包里的测试用例
   *
   * 参考：
   * <databaseIdProvider type="VENDOR">
   *   <property name="SQL Server" value="sqlserver"/>
   *   <property name="DB2" value="db2"/>
   *   <property name="Oracle" value="oracle" />
   * </databaseIdProvider>
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      // 获取数据库厂商别名
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility —— 保持向后兼容性的糟糕补丁
      // 与老版本兼容
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR"/* 数据库厂商 */;
      }
      // 获取配置好的数据库名称与id键值对集合
      Properties properties = context.getChildrenAsProperties();
      // "DB_VENDOR"-->VendorDatabaseIdProvider
      // 创建DatabaseIdProvider对象
      // 先根据"数据库厂商别名"，去类型别名注册器当中获取到对应的Class；然后实例化"数据库厂商"
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
      // 配置DatabaseIdProvider，完成初始化
      databaseIdProvider.setProperties(properties);
    }

    /* 获取数据库id */
    // 获取数据库环境对象
    Environment environment = configuration.getEnvironment();
    // 解析databaseId的方式：
    // 前面已经组件好了数据源对象，有了数据源，我们可以获取到数据源里面的url，有了数据源的url之后，
    // 每一个数据库在进行连接的时候，它具体的url是不一样的，jdbc:mysql、jdbc:oracle，可以根据url里面的标识来进行解析工作
    if (environment != null && databaseIdProvider != null) {
      // 得到当前的databaseId，可以调用DatabaseMetaData.getDatabaseProductName()得到诸如"Oracle (DataDirect)"的字符串，
      // 然后和预定义的property比较,得出目前究竟用的是什么数据库
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  /**
   * 事务管理器
   *
   * 解析<transactionManager>标签，也就是实例化事务管理器
   *
   * 参考：
   * <transactionManager type="JDBC">
   *   <property name="..." value="..."/>
   * </transactionManager>
   */
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      // 获取事务管理器的别名
      String type = context.getStringAttribute("type");
      // 获取配置的事务管理器属性
      Properties props = context.getChildrenAsProperties();
      /**
       * 1、resolveClass(type)：根据"事务管理器的别名"，去类型别名注册器"typeAliasRegistry"中，获取对应的事务管理器Class
       *
       * 例如：<transactionManager type="JDBC">，别名是JDBC，得到的factory = JdbcTransactionFactory
       */
      // 先根据"事务管理器的别名"，去类型别名注册器"typeAliasRegistry"中，获取对应的事务管理器Class；然后实例化事务管理器
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 往事务管理器当中设置属性
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  /**
   * 解析<dataSource>标签，也就是实例化数据源工厂
   *
   * 参考：
   * <dataSource type="POOLED">
   *   <property name="driver" value="${driver}"/>
   *   <property name="url" value="${url}"/>
   *   <property name="username" value="${username}"/>
   *   <property name="password" value="${password}"/>
   * </dataSource>
   */
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      // 获取数据源工厂别名
      String type = context.getStringAttribute("type");
      // 获取设置的数据源工厂属性，里面包含：数据库驱动器、url、用户名、密码
      Properties props = context.getChildrenAsProperties();
      // 先根据"数据源工厂别名"，去类型别名注册器"typeAliasRegistry"中，获取对应的数据源工厂Class，然后实例化数据源工厂
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 往数据源工厂里面设置属性
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

 /**
  * 类型处理器
  *
  * 解析<typeHandlers>标签
  * >>> 也就是注册类型处理器到Configuration
  *
  * <typeHandlers>
  *      <typeHandler handler="org.mybatis.example.ExampleTypeHandler"/>
  * </typeHandlers>
  * or
  * <typeHandlers>
  *      <package name="org.mybatis.example"/>
  * </typeHandlers>
  */
  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        /* 1、扫描指定包下的TypeHandler，往"java类型处理器集合"当中注册 */
        // 如果是package
        if ("package".equals(child.getName())) {
          // 获取类型处理器包名称
          String typeHandlerPackage = child.getStringAttribute("name");
          // 扫描指定包下的TypeHandler，往java类型处理器集合当中注册
          typeHandlerRegistry.register(typeHandlerPackage);
        }
        /* 2、直接把配置的TypeHandler，往java类型处理器集合当中注册 */
        // 如果是typeHandler
        else {
          // java类型别名
          String javaTypeName = child.getStringAttribute("javaType");
          // jdbc类型别名
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          // 类型处理器别名(也可以是全限定名)
          String handlerTypeName = child.getStringAttribute("handler");

          // 根据"java类型别名"去类型别名注册器当中，获取对应的java类型
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          // 根据"jdbc类型别名"去类型别名注册器当中，获取对应的jdbc类型
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          // 根据"类型处理器别名"去类型别名注册器当中，获取对应的类型处理器
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);

          /* 以下都是往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"，只不过，以下是3种不同的参数形式 */

          // 如果java类型不为空
          if (javaTypeClass != null) {
            /* （1）如果java类型不为空，但是jdbc类型为空 */
            if (jdbcType == null) {
              // 往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"
              // ⚠️注意：里面会获取类上的@MappedJdbcTypes，得到jdbc类型
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            }
            /* （2）如果java类型不为空，且jdbc类型也不为空 */
            else {
              // 往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"
              // 题外：该方法的调用和上面方法的调用一样，只不过上面方法获取了类上的@MappedJdbcTypes，将@MappedJdbcTypes中配置的jdbc类型，而这里是直接使用已有的jdbc类型
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          }
          else {
            /* （3）如果java类型为空 */
            // 往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"
            // 注意：⚠️里面会获取类上的@MappedTypes，得到java类型
            // 注意：⚠️里面会获取类上的@MappedJdbcTypes，得到jdbc类型
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 注册映射器（注册mapper）
   *
   * 解析<mappers>标签
   * >>> 注册映射器到Configuration
   *
   * 参考：
   * 1、使用"相对于类路径"的资源引用
   * <mappers>
   *    <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
   *    <mapper resource="org/mybatis/builder/BlogMapper.xml"/>
   *    <mapper resource="org/mybatis/builder/PostMapper.xml"/>
   * </mappers>
   *
   * 2、使用"完全限定资源"定位符
   * <mappers>
   *    <mapper url="file:///var/mappers/AuthorMapper.xml"/>
   *    <mapper url="file:///var/mappers/BlogMapper.xml"/>
   *    <mapper url="file:///var/mappers/PostMapper.xml"/>
   * </mappers>
   *
   * 3、使用mapper接口的全限定类名
   * <mappers>
   *    <mapper class="org.mybatis.builder.AuthorMapper"/>
   *    <mapper class="org.mybatis.builder.BlogMapper"/>
   *    <mapper class="org.mybatis.builder.PostMapper"/>
   * </mappers>
   *
   * 4、指定包路径，自动扫描包下的所有映射文件
   * <mappers>
   *    <package name="org.mybatis.builder"/>
   * </mappers>
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        /*

        1、扫描注册指定包下的mapper（扫描指定包下的所有映射文件，注册对应的映射器）
        >>> 1、获取包下所有的Object类型的类，由于接口也是继承Object，所以会被识别到
        >>> 2、然后会判断，只有是接口，才会进行解析和注册
        >>> 3、注册mapper接口流程：
        >>> 3.1、先是构建mapper接口对应的MapperProxyFactory，用于生成mapper接口的代理对象；并将它两的对应关系，存入knownMappers（已知mapper）集合中
        >>> 3.2、然后去解析mapper接口，分为2部分：
        >>> （1）、先解析mapper接口对应的dao.xml文件，将对应信息放入Configuration；—— 配置文件开发
        >>> （2）、然后再解析mapper接口（把mapper接口作为映射文件进行解析），将对应信息放入Configuration—— 注解开发

        题外：映射文件就是mapper文件，一个意思，也可以称呼为"mapper配置文件"

        */
        if ("package".equals(child.getName())) {
          // 获取mapper包名称
          String mapperPackage = child.getStringAttribute("name");
          // 扫描指定包下的所有映射器(mapper接口)，然后注册mapper到Configuration.mapperRegistry.knownMappers(已知mapper集合)中
          configuration.addMappers(mapperPackage);
        } else {

          // 获取mapper节点的resource、 url、class属性（注意：⚠️三个属性互斥）
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");

          /*

          2、直接注册"resource指定的dao.xml"对应的的mapper，也就是直接解析对应的dao.xml文件，将对应信息放入Configuration

          题外：resource：相对类路径指定的dao.xml

          */
          /**
           * <mappers>
           *    <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
           *    <mapper resource="org/mybatis/builder/PostMapper.xml"/>
           * </mappers>
           */
          // 使用类路径
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
              // 映射器比较复杂，调用XMLMapperBuilder
              // 注意在for循环里每个mapper都重新new一个XMLMapperBuilder，来解析
              XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
              // ⚠️解析dao.xml
              mapperParser.parse();
            }
          }

          /*

          3、直接注册"url指定的dao.xml"对应的的mapper，也就是直接解析对应的dao.xml文件，将对应信息放入Configuration

          题外：resource：绝对路径指定的dao.xml

          */
          /**
           * <mappers>
           *    <mapper url="file:///var/mappers/AuthorMapper.xml"/>
           *    <mapper url="file:///var/mappers/BlogMapper.xml"/>
           * </mappers>
           */
          // 使用绝对url路径
          else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            try (InputStream inputStream = Resources.getUrlAsStream(url)) {
              //映射器比较复杂，调用XMLMapperBuilder
              XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
              // 解析dao.xml
              mapperParser.parse();
            }
          }

          /*

          4、直接注册"class指定的mapper接口(dao接口)"对应的mapper，也就是：
          >>> （1）、先解析mapper接口对应的dao.xml文件，将对应信息放入Configuration；—— 配置文件开发
          >>> （2）、然后再解析mapper接口（把mapper接口作为映射文件进行解析），将对应信息放入Configuration—— 注解开发

          题外：class：接口全限定名，例如：org.mybatis.builder.UserDao

          */
          /**
           * <mappers>
           *    <mapper class="org.mybatis.builder.UserDao"/>
           *    <mapper class="org.mybatis.builder.AccountDao"/>
           * </mappers>
           */
          // 使用java类名
          else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            /**
             * 注册mapper接口：会判断，必须是接口，才会进行加载和解析
             * 1、先是构建mapper接口对应的MapperProxyFactory，用于生成mapper接口的代理对象；并将它两的对应关系，存入knownMappers（已知mapper）集合中
             * 2、然后去解析mapper接口，分为2部分
             * （1）先解析mapper接口对应的dao.xml文件；—— 配置文件开发
             * （2）然后再解析mapper接口（把mapper接口作为映射文件进行解析）—— 注解开发
             */
            // ⚠️直接把这个映射加入配置
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  // 比较id和environment是否相等
  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    }
    if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    }
    return environment.equals(id);
  }

}
