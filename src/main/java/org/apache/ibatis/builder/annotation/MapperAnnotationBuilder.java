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
package org.apache.ibatis.builder.annotation;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.CacheNamespaceRef;
import org.apache.ibatis.annotations.Case;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Options.FlushCachePolicy;
import org.apache.ibatis.annotations.Property;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.TypeDiscriminator;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.FetchType;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * 注解方式构建mapper（对注解方式构建mapper进行的扩展，支持先解析dao.xml，然后再解析dao接口，这2种方式构建mapper）
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class MapperAnnotationBuilder {

  // @Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider
  private static final Set<Class<? extends Annotation>> statementAnnotationTypes = Stream
    .of(Select.class, Update.class, Insert.class, Delete.class, SelectProvider.class, UpdateProvider.class,
      InsertProvider.class, DeleteProvider.class)
    .collect(Collectors.toSet());

  private final Configuration configuration;
  private final MapperBuilderAssistant assistant;
  // mapper接口 Class
  private final Class<?> type;

  public MapperAnnotationBuilder(Configuration configuration, Class<?> type) {
    // 例如：com.msb.mybatis_02.dao.UserDao = com/msb/mybatis_02/dao/UserDao.java (best guess)
    String resource = type.getName().replace('.', '/') + ".java (best guess)"/* 最佳的揣测 */;

    // ⚠️创建属于当前MapperAnnotationBuilder的MapperBuilderAssistant
    this.assistant = new MapperBuilderAssistant(configuration, resource);
    this.configuration = configuration;

    // mapper接口 Class
    this.type = type;
  }

  /**
   * 解析mapper（单个mapper接口）
   * 1、先解析mapper接口对应的dao.xml文件；—— 配置文件开发
   * 2、然后再解析mapper接口（把mapper接口作为mapper配置文件进行解析）—— 注解开发
   */
  public void parse() {
    // 接口全限定名
    String resource = type.toString();
    // 检测是否己经加载过该mapper接口
    // 加载过，就不加载了
    if (!configuration.isResourceLoaded(resource)) {
      /* 没有加载过 */

      /*

      一、（配置文件开发）解析mapper接口对应的dao.xml文件（加载和解析），将解析到的信息注册到configuration

      题外：将解析到的信息注册到configuration
      题外："mapper接口对应的dao.xml文件地址" = 获取mapper接口的全限定名；然后替换.为/；再拼接上.xml

       */
      /**
       * 我们写的一个Dao接口，要么是有一个与之对应的xml文件，xml文件里面才会写具体的sql语句；或者是在接口的方法上面写对应的注解，定义sql
       */
      // ⚠️检测是否加载过对应的映射配置文件，如果未加载，则创建XMLMapperBuilder对象解析对应的映射文件
      loadXmlResource();

      // 添加当前"接口全限定名"为已经加载的资源，后续不会重复解析当前mapper
      configuration.addLoadedResource(resource);

      /*

      二、（注解开发）解析mapper接口（把mapper接口作为mapper配置文件进行解析了），将解析到的信息注册到configuration

      */

      /* 1、设置当前的命名空间为"接口全限定名"，因为接下来要把mapper接口作为mapper配置文件进行解析了！ */
      /**
       * 1、疑惑：在上面调用loadXmlResource()的时候，已经往MapperBuilderAssistant中设置了currentNamespace，
       * 为什么此时的MapperBuilderAssistant中没有currentNamespace呢？
       * 查阅上诉代码，也并没未发现有把MapperBuilderAssistant中currentNamespace置空操作，那为什么当前MapperBuilderAssistant中currentNamespace为null呢？
       *
       * 原因：查阅上诉代码发现，在解析dao.xml的时候，创建了一个XMLMapperBuilder，用于转换解析dao.xml文件的。
       * 在创建XMLMapperBuilder的时候，构造器内部单独创建了一个只属于XMLMapperBuilder的MapperBuilderAssistant，与当前的MapperBuilderAssistant不是同一个对象！
       * 当前的MapperBuilderAssistant是只属于当前MapperAnnotationBuilder的，同时当前MapperBuilderAssistant中并未设置currentNamespace，所以为null
       *
       * 题外：当前的MapperBuilderAssistant是只属于当前MapperAnnotationBuilder的，而XMLMapperBuilder中的MapperBuilderAssistant是属于XMLMapperBuilder的！
       */
      assistant.setCurrentNamespace(type.getName());

      /* 2、解析接口上的注解 */
      /* 2.1、解析接口上的@CacheNamespace注解，也就是：通过@CacheNamespace注解信息，创建一个Cache对象，并添加到configuration.caches集合中 */
      parseCache();
      /* 2.2、解析接口上的@CacheNamespaceRef，也就是：从configuration.caches中，通过@CacheNamespaceRef配置的引用缓存的命名空间，去获取对应命名空间的缓存(Cache)，作为当前mapper接口的引用缓存 */
      parseCacheRef();

      /* 3、遍历每一个接口方法，解析上面的注解 */
      for (Method method : type.getMethods()) {
        /* 3.1、判断当前方法，是不是桥接方法或默认方法，是的话，就跳过，因为不允许在这2种方法上定义注解 */
        // 判断当前方法是不是可以声明sql语句的方法，
        // 也就是当前方法不是桥接方法，也不是默认方法，才为true；因为只有当前方法不是桥接方法，也不是默认方法，才能声明sql语句
        if (!canHaveStatement/* 可以有声明 */(method)) {
          continue;
        }

        /*

        3.2、根据方法上的"结果映射"注解，构建方法对应的ResultMap，放入到Configuration.resultMaps中

        （1）判断当前方法上面是不是携带了，归属于当前databaseId下的，@Select、@SelectProvider中的某一个注解，且当前方法上不存在@ResultMap；
        （2）携带了就解析方法上的@Arg、@Result、@TypeDiscriminator信息，创建对应的ResultMap对象，然后添加到Configuration.resultMaps中

        */
        /**
         * 1、getAnnotationWrapper(method, false, Select.class, SelectProvider.class)：
         * 获取方法上的@Select、@SelectProvider其中一个注解，为其创建AnnotationWrapper进行返回
         *
         * 注意：getAnnotationWrapper(method, false, Select.class, SelectProvider.class)会校验，一个方法上，同一个databaseId下，@Select、@SelectProvider注解只能定义一个！
         */
        // 4.2.1、判断当前方法上面是不是携带了，归属于当前databaseId下的，@Select、@SelectProvider中的某一个注解，且当前方法上不存在@ResultMap；
        if (getAnnotationWrapper(method, false, Select.class, SelectProvider.class).isPresent()
            // 方法不存在@ResultMap
            && method.getAnnotation(ResultMap.class) == null) {

          // 4.2.2、解析方法上的@Arg、@Result、@TypeDiscriminator信息，创建对应的ResultMap对象，然后添加到configuration.resultMaps中
          // 简单概括：解析方法上的一些注解信息，构建对应的ResultMap对象，然后添加到configuration.resultMaps中
          parseResultMap(method);
        }

        /*

        3.3、解析方法上sql相关的注解，构建MappedStatement，放入到Configuration.mappedStatements中

        （1）解析方法上的@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider注解，
        （2）然后用注解里面的信息(比如：sql语句、参数类型，resultMap集合（之前解析好的，这里通过resultMapId获取到）、sql命令类型、执行sql的对象类型，等信息)，创建MappedStatement对象，
        （3）放入到Configuration.mappedStatements里面去

        注意：同一个databaseId下，只允许存在@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider中的一个注解

        */
        try {
          parseStatement(method);
        } catch (IncompleteElementException/* 不完整元素异常 */ e) {
          /* 3.3.1、如果解析过程出现IncompleteElementException异常，则将当前方法对应的MethodResolver，放入到Configuration.incompleteMethod集合中保存 */
          // 如果解析过程出现IncompleteElementException异常，可能是引用了未解析的注解，此处将出现异常的方法添加到incompleteMethod集合中保存
          configuration.addIncompleteMethod(new MethodResolver(this, method));
        }
      }

    }

    /* 4、重新处理解析失败的"方法上的sql注解" */
    // 遍历Configuration.incompleteMethod集合中记录的解析失败的方法，对其进行重新解析，最终调用的还是parseStatement(method)
    parsePendingMethods();
  }

  /**
   * 判断当前方法是不是可以声明sql语句的方法，
   * 也就是当前方法不是桥接方法，也不是默认方法，才为true；因为只有当前方法不是桥接方法，也不是默认方法，才能声明sql语句
   */
  private boolean canHaveStatement(Method method) {
    // issue #237
    // 题外：默认方法是指：jdk8引入的接口可以定义的默认方法
    return !method.isBridge()/* 不是桥接方法 */ && !method.isDefault()/* 不是接口的默认方法 */;
  }

  private void parsePendingMethods() {
    Collection<MethodResolver> incompleteMethods = configuration.getIncompleteMethods();
    synchronized (incompleteMethods) {
      Iterator<MethodResolver> iter = incompleteMethods.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // This method is still missing a resource
        }
      }
    }
  }

  /**
   * 加载和解析mapper接口对应的dao.xml文件！会将解析的信息注册到configuration
   * <p>
   * 题外："mapper接口对应的dao.xml文件地址"由来：获取mapper接口的全限定名，然后替换.为/，再拼接上.xml，则得到⚠️【mapper接口对应的dao.xml文件路径】
   */
  private void loadXmlResource() {
    // Spring may not know the real resource name so we check a flag
    // to prevent loading again a resource twice
    // this flag is set at XMLMapperBuilder#bindMapperForNamespace
    // 上面的翻译：Spring可能不知道真正的资源名称，因此我们检查一个标志，以防止再次加载资源两次此标志设置在XMLMapperBuilder#bindMapperForNamespace()

    // 检查是否加载过该mapper接口
    if (!configuration.isResourceLoaded("namespace:" + type.getName())) {
      /* 没有加载过该mapper接口 */

      /* 1、获取mapper接口的全限定名，然后替换.为/，再拼接上.xml，则得到⚠️【mapper接口对应的dao.xml文件路径】 */
      /**
       * type.getName()：获取mapper接口的全限定名，例如：com.msb.mybatis_02.dao.UserDao
       */
      // 获取mapper接口对应的xml文件路径：获取mapper接口的全限定名，然后替换.为/，再拼接上.xml，则得到mapper接口对应的dao.xml文件路径
      // 例如：com.msb.mybatis_02.dao.UserDao，得到com/msb/mybatis_02/dao/UserDao.xml
      String xmlResource = type.getName().replace('.', '/') + ".xml";

      /* 2、通过"mapper接口对应的xml文件路径"，加载dao.xml */

      // #1347
      InputStream inputStream = type.getResourceAsStream("/" + xmlResource);
      if (inputStream == null) {
        // Search XML mapper that is not in the module but in the classpath.
        // 上面翻译：搜索不在模块中但在类路径中的XML映射器。
        try {
          // 题外：把配置文件通过IO流的方式读取回来，形成一个document，然后去解析里面的标签和属性
          inputStream = Resources.getResourceAsStream(type.getClassLoader(), xmlResource);
        } catch (IOException e2) {
          // ignore, resource is not required —— 忽略，不需要资源
        }
      }

      /* 3、解析dao.xml */
      if (inputStream != null) {
        // 创建XMLMapperBuilder
        // 注意：里面：⚠️在未解析dao.xml之前，设置当前命名空间为当前"mapper接口全限定名"
        XMLMapperBuilder xmlParser = new XMLMapperBuilder(inputStream, assistant.getConfiguration(), xmlResource, configuration.getSqlFragments(), type.getName());
        // ⚠️解析dao.xml
        xmlParser.parse();
      }
    }
  }

  /**
   * 解析@CacheNamespace注解：
   * >>> 通过@CacheNamespace注解信息，创建一个Cache对象，并添加到configuration.caches集合中
   */
  private void parseCache() {
    CacheNamespace cacheDomain = type.getAnnotation(CacheNamespace.class);
    if (cacheDomain != null) {
      // 获取缓存大小
      Integer size = cacheDomain.size() == 0 ? null : cacheDomain.size();
      // 刷新间隔
      Long flushInterval = cacheDomain.flushInterval() == 0 ? null : cacheDomain.flushInterval();
      // 缓存属性
      Properties props = convertToProperties(cacheDomain.properties());
      // 创建Cache对象，并添加到configuration.caches集合中
      assistant.useNewCache/* @CacheNamespace */(cacheDomain.implementation(), cacheDomain.eviction(), flushInterval, size, cacheDomain.readWrite(), cacheDomain.blocking(), props);
    }
  }

  private Properties convertToProperties(Property[] properties) {
    if (properties.length == 0) {
      return null;
    }
    Properties props = new Properties();
    for (Property property : properties) {
      props.setProperty(property.name(),
        PropertyParser.parse(property.value(), configuration.getVariables()));
    }
    return props;
  }

  /**
   * 解析@CacheNamespaceRef，也就是：从configuration.caches中，通过@CacheNamespaceRef配置的引用缓存的命名空间，去获取对应命名空间的缓存(Cache)，作为当前mapper接口的引用缓存
   */
  private void parseCacheRef() {
    // @CacheNamespaceRef作用：引用其它命名空间的缓存
    CacheNamespaceRef cacheDomainRef = type.getAnnotation(CacheNamespaceRef.class);
    if (cacheDomainRef != null) {
      Class<?> refType = cacheDomainRef.value();
      String refName = cacheDomainRef.name();
      if (refType == void.class && refName.isEmpty()) {
        throw new BuilderException("Should be specified either value() or name() attribute in the @CacheNamespaceRef");
      }
      if (refType != void.class && !refName.isEmpty()) {
        throw new BuilderException("Cannot use both value() and name() attribute in the @CacheNamespaceRef");
      }
      // 获取引用缓存的命名空间
      String namespace = (refType != void.class) ? refType.getName() : refName;
      try {

        /* 1、从configuration.caches中，通过@CacheNamespaceRef配置的引用缓存的命名空间，去获取对应命名空间的缓存(Cache)，作为当前mapper接口的引用缓存 */
        assistant.useCacheRef(namespace);
      } catch (IncompleteElementException e) {

        /*

        2、有可能，目前configuration.caches中不存在引用的缓存，所以报IncompleteElementException错误，
        于是把CacheRefResolver，添加到configuration.incompleteCacheRefs中，等待所有的映射文件都解析完了，那个时候，所有的缓存都已经注册了，
        那个时候再去configuration.caches中寻找引用的缓存，可能就寻找得到了！

        */

        // 报错了，将对应的CacheRefResolver，添加到configuration.incompleteCacheRefs中，后续再解析
        configuration.addIncompleteCacheRef(new CacheRefResolver(assistant, namespace));
      }
    }
  }

  /**
   * 解析方法上的@Arg、@Result、@TypeDiscriminator，用其信息创建对应的ResultMap对象，然后添加到configuration.resultMaps中
   */
  private String parseResultMap(Method method) {
    /* 1、获取方法的返回值类型 */
    Class<?> returnType = getReturnType(method);

    /* 2、获取方法上的@Arg */
    Arg[] args = method.getAnnotationsByType(Arg.class);
    /* 3、获取方法上的@Result */
    Result[] results = method.getAnnotationsByType(Result.class);
    /* 4、获取方法上的@TypeDiscriminator */
    TypeDiscriminator typeDiscriminator/* 类型鉴别器 */ = method.getAnnotation(TypeDiscriminator.class);

    /*

    5、生成resultMapId（结果映射名称）：
    （1）存在@Results的id属性，则用【类全限定名 + id】等于结果映射名称
    （2）不存在，则用【类全限定名 + 方法名 + 方法参数简单名称/-void(不存在方法参数的情况下)】等于结果映射名称

     */
    String resultMapId = generateResultMapName/* 生成结果映射名称 */(method);

    /*

    6、用resultMapId、returnType、@Arg、@Result、@TypeDiscriminator信息构建一个ResultMap，然后添加到configuration.resultMaps中

     */
    // ⚠️创建对应的ResultMap对象，然后添加到configuration.resultMaps中
    applyResultMap(resultMapId, returnType, args, results, typeDiscriminator);

    /* 7、返回当前resultMapId */
    return resultMapId;
  }

  /**
   * 生成结果映射名称：
   * 1、存在@Results的id属性，则用【类全限定名 + id】等于结果映射名称
   * 2、不存在，则用【类全限定名 + 方法名 + 方法参数简单名称/-void(不存在方法参数的情况下)】等于结果映射名称
   */
  private String generateResultMapName(Method method) {
    Results results = method.getAnnotation(Results.class);


    /* 1、存在@Results的id属性，则用【类全限定名 + id】等于结果映射名称 */
    if (results != null && !results.id().isEmpty()) {
      // 类全限定名 + id
      return type.getName() + "." + results.id();
    }

    /* 2、不存在，则用【类全限定名 + 方法名 + 方法参数简单名称/-void(不存在方法参数的情况下)】等于结果映射名称 */
    StringBuilder suffix = new StringBuilder();

    // 存在方法参数，则拼接上所有的方法参数简单名称
    for (Class<?> c : method.getParameterTypes()/* 方法参数类型 */) {
      suffix.append("-");
      // 方法参数类型的简单名称
      suffix.append(c.getSimpleName());
    }

    // 不存在方法参数，则用-void
    if (suffix.length() < 1) {
      suffix.append("-void");
    }
    // 类全限定名 + 方法名 + 方法参数简单名称/-void
    return type.getName() + "." + method.getName() + suffix;
  }

  /**
   * 创建对应的ResultMap对象，然后添加到configuration.resultMaps中
   *
   * @param resultMapId
   * @param returnType
   * @param args
   * @param results
   * @param discriminator
   */
  private void applyResultMap(String resultMapId, Class<?> returnType, Arg[] args, Result[] results, TypeDiscriminator discriminator) {
    /* 1、创建一个结果映射集合 */
    List<ResultMapping> resultMappings = new ArrayList<>();

    /*

    2、解析所有@Arg信息；一个@Arg，构建一个ResultMapping对象；然后放入到resultMappings中

    题外：@Arg参数作为构造参数的结果映射

    */
    applyConstructorArgs(args, returnType, resultMappings);

    /* 3、解析所有@Result信息；一个@Result，构建一个ResultMapping对象；然后放入到resultMappings中 */
    applyResults(results, returnType, resultMappings);

    /* 4、应用鉴别器 */
    Discriminator disc = applyDiscriminator(resultMapId, returnType, discriminator);

    // TODO add AutoMappingBehaviour
    /* 5、⚠️创建对应的ResultMap对象，然后添加到configuration.resultMaps中 */
    assistant.addResultMap(resultMapId, returnType, null, disc, resultMappings, null);

    createDiscriminatorResultMaps(resultMapId, returnType, discriminator);
  }

  private void createDiscriminatorResultMaps(String resultMapId, Class<?> resultType, TypeDiscriminator discriminator) {
    if (discriminator != null) {
      for (Case c : discriminator.cases()) {
        String caseResultMapId = resultMapId + "-" + c.value();
        List<ResultMapping> resultMappings = new ArrayList<>();
        // issue #136
        applyConstructorArgs(c.constructArgs(), resultType, resultMappings);
        applyResults(c.results(), resultType, resultMappings);
        // TODO add AutoMappingBehaviour
        assistant.addResultMap(caseResultMapId, c.type(), resultMapId, null, resultMappings, null);
      }
    }
  }

  private Discriminator applyDiscriminator(String resultMapId, Class<?> resultType, TypeDiscriminator discriminator) {
    if (discriminator != null) {
      String column = discriminator.column();
      Class<?> javaType = discriminator.javaType() == void.class ? String.class : discriminator.javaType();
      JdbcType jdbcType = discriminator.jdbcType() == JdbcType.UNDEFINED ? null : discriminator.jdbcType();
      @SuppressWarnings("unchecked")
      Class<? extends TypeHandler<?>> typeHandler = (Class<? extends TypeHandler<?>>)
        (discriminator.typeHandler() == UnknownTypeHandler.class ? null : discriminator.typeHandler());
      Case[] cases = discriminator.cases();
      Map<String, String> discriminatorMap = new HashMap<>();
      for (Case c : cases) {
        String value = c.value();
        String caseResultMapId = resultMapId + "-" + value;
        discriminatorMap.put(value, caseResultMapId);
      }
      return assistant.buildDiscriminator(resultType, column, javaType, jdbcType, typeHandler, discriminatorMap);
    }
    return null;
  }

  /**
   * 解析方法上的@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider注解，
   * 然后用注解里面的信息(比如：sql语句、参数类型，resultMap集合（之前解析好的，这里通过resultMapId获取到）、sql命令类型、执行sql的对象类型，等信息)，创建MappedStatement对象，
   * 放入到Configuration.mappedStatements里面去
   *
   * 注意：同一个databaseId下，只允许存在@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider中的一个注解
   */
  void parseStatement(Method method) {

    // 获取方法的参数类型：
    // 1、如果方法上不存在参数，则返回null
    // 2、如果方法上只存在1个参数，则返回这1个方法参数类型
    // 3、如果方法上存在2个及2个以上参数，则固定返回ParamMap.class
    final Class<?> parameterTypeClass = getParameterType(method);

    // 解析@Lang，获取语言驱动器（默认：XMLLanguageDriver）
    final LanguageDriver languageDriver = getLanguageDriver(method);

    /*

    1、解析方法上的@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider注解，
    然后用注解里面的信息(比如：sql语句、参数类型，resultMap集合（之前解析好的，这里通过resultMapId获取到）、sql命令类型、执行sql的对象类型，等信息)，创建MappedStatement对象，
    放入到Configuration.mappedStatements里面去


    注意：同一个databaseId下，只允许存在@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider中的一个注解

     */
    /**
     * 1、statementAnnotationTypes：@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider
     *
     * 2、getAnnotationWrapper(method, true, statementAnnotationTypes)：
     * 查看方法上是否存在@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider这些sql注解，
     * 如果存在，则获取这些注解，并为每个注解创建其对应的AnnotationWrapper对象（AnnotationWrapper里面包含该注解，以及注解上的databaseId、以及根据注解类型得到SqlCommandType）；
     * 然后校验，只允许，同一databaseId下，只能存在@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider中的一个注解，否则报错！
     * 如果校验通过，则获取当前方法上，属于当前databaseId下的，唯一的AnnotationWrapper，进行返回！
     */
    // 获取方法上对应的sql注解，然后创建对应的AnnotationWrapper返回（AnnotationWrapper里面包含该注解，以及注解上的databaseId、以及根据注解类型得到SqlCommandType）；
    getAnnotationWrapper(method, true, statementAnnotationTypes)
      // 如果方法上存在@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider中的某一个注解
      .ifPresent(statementAnnotation/* AnnotationWrapper */ -> {

        /* 获取@Select、@Update、@Insert、@Delete、@SelectKey里面的sql语句，构建为一个SqlSource对象 */
        final SqlSource sqlSource = buildSqlSource(statementAnnotation.getAnnotation()/* 获取注解 */, parameterTypeClass, languageDriver, method);

        // 获取sql命令类型
        final SqlCommandType sqlCommandType = statementAnnotation.getSqlCommandType();
        // 获取方法上的@Options
        // 注意：之所以走这么复杂的流程，是为了校验，getAnnotationWrapper()可以起到校验的作用：校验，只允许，在同一个databaseId的情况下，方法上只能定义一个@Options
        final Options options = getAnnotationWrapper(method, false, Options.class).map(x -> (Options) x.getAnnotation()).orElse(null);

        // mappedStatementId = mapper接口全限定名称 + 方法名称
        final String mappedStatementId = type.getName() + "." + method.getName();

        /* 解析@SelectKey信息，构建KeyGenerator */
        final KeyGenerator keyGenerator;
        String keyProperty = null;
        String keyColumn = null;
        if (SqlCommandType.INSERT.equals(sqlCommandType)/* insert */ || SqlCommandType.UPDATE.equals(sqlCommandType)/* update */) {
          // first check for SelectKey annotation - that overrides everything else —— 首先检查SelectKey注解-覆盖其他所有内容

          // 获取方法上的@SelectKey
          // 题外：从getAnnotationWrapper()得知，方法上只允许定义一个@SelectKey
          SelectKey selectKey = getAnnotationWrapper(method, false, SelectKey.class).map(x -> (SelectKey) x.getAnnotation()).orElse(null);
          if (selectKey != null) {
            keyGenerator = handleSelectKeyAnnotation(selectKey, mappedStatementId, getParameterType(method), languageDriver);
            keyProperty = selectKey.keyProperty();
          } else if (options == null) {
            keyGenerator = configuration.isUseGeneratedKeys() ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
          } else {
            keyGenerator = options.useGeneratedKeys() ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
            keyProperty = options.keyProperty();
            keyColumn = options.keyColumn();
          }
        } else {
          // 不存在keyGenerator
          keyGenerator = NoKeyGenerator.INSTANCE/* NoKeyGenerator对象 */;
        }

        Integer fetchSize = null;
        Integer timeout = null;
        /* 执行sql的对象类型 */
        StatementType statementType = StatementType.PREPARED;
        /* 返回值类型 */
        ResultSetType resultSetType = configuration.getDefaultResultSetType();
        // 是否是查询语句
        boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
        boolean flushCache = !isSelect;
        boolean useCache = isSelect;
        if (options != null) {
          if (FlushCachePolicy.TRUE.equals(options.flushCache())) {
            flushCache = true;
          } else if (FlushCachePolicy.FALSE.equals(options.flushCache())) {
            flushCache = false;
          }
          useCache = options.useCache();
          fetchSize = options.fetchSize() > -1 || options.fetchSize() == Integer.MIN_VALUE ? options.fetchSize() : null; //issue #348
          timeout = options.timeout() > -1 ? options.timeout() : null;
          // 执行sql的对象类型
          statementType = options.statementType();
          if (options.resultSetType() != ResultSetType.DEFAULT) {
            resultSetType = options.resultSetType();
          }
        }

        /*

        生成resultMapId(结果映射名称)：
        1、存在id属性，则用【类全限定名 + id】等于结果映射名称
        2、不存在id属性，则用【类全限定名 + 方法名 + 方法参数简单名称/-void(不存在方法参数的情况下)】等于结果映射名称

        */
        // 结果映射名称
        String resultMapId = null;
        if (isSelect) {
          ResultMap resultMapAnnotation = method.getAnnotation(ResultMap.class);
          if (resultMapAnnotation != null) {
            resultMapId = String.join(",", resultMapAnnotation.value());
          } else {

            resultMapId = generateResultMapName(method);
          }
        }

        /*

        上面是解析sql注解中配置的信息，然后这里是：根据sql注解配置的信息，构建成MappedStatement对象；然后注册MappedStatement到configuration的mappedStatements中（映射语句集合）
        >>> key（String）：MappedStatement.id = mapper接口全限定名 + 方法名 / 命名空间+标签id / 包名+类名+方法名
        >>> value（MappedStatement）：sql标签，或者说是sql注解信息，构建而成的MappedStatement对象

        题外：上面是解析sql注解中配置的信息

         */
        assistant.addMappedStatement(
          mappedStatementId,
          sqlSource,
          statementType,
          sqlCommandType,
          fetchSize,
          timeout,
          // ParameterMapID
          null,
          parameterTypeClass,
          // 结果映射名称
          resultMapId,
          getReturnType(method),
          resultSetType,
          flushCache,
          useCache,
          // TODO gcode issue #577
          false,
          keyGenerator,
          keyProperty,
          keyColumn,
          statementAnnotation.getDatabaseId(),
          languageDriver,
          // ResultSets
          options != null ? nullOrEmpty(options.resultSets()) : null);
      });
  }

  /**
   * 解析@Lang，获取语言驱动器
   */
  private LanguageDriver getLanguageDriver(Method method) {
    /* 1、解析@Lang，得出语言驱动器类型 */
    // 解析@Lang，得出语言驱动器类型
    Lang lang = method.getAnnotation(Lang.class);
    Class<? extends LanguageDriver> langClass = null;
    if (lang != null) {
      // 语言驱动器类型
      langClass = lang.value();
    }

    /* 2、根据语言驱动器类型，获取语言驱动器（里面根据语言驱动器类型，实例化语言驱动器，然后返回） */
    return configuration.getLanguageDriver(langClass);
  }

  /**
   * 获取方法的参数类型：
   * 1、如果方法上不存在参数，则返回null
   * 2、如果方法上只存在1个参数，则返回这1个方法参数类型
   * 3、如果方法上存在2个及2个以上参数，则固定返回ParamMap.class
   *
   * 如果方法参数值
   *
   * @param method
   * @return
   */
  private Class<?> getParameterType(Method method) {
    Class<?> parameterType = null;
    Class<?>[] parameterTypes = method.getParameterTypes();
    for (Class<?> currentParameterType : parameterTypes) {
      if (!RowBounds.class.isAssignableFrom(currentParameterType) && !ResultHandler.class.isAssignableFrom(currentParameterType)) {
        if (parameterType == null) {
          parameterType = currentParameterType;
        } else {
          // issue #135
          parameterType = ParamMap/* 参数映射 */.class;
        }
      }
    }
    return parameterType;
  }

  private Class<?> getReturnType(Method method) {
    Class<?> returnType = method.getReturnType();
    Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, type);
    if (resolvedReturnType instanceof Class) {
      returnType = (Class<?>) resolvedReturnType;
      if (returnType.isArray()) {
        returnType = returnType.getComponentType();
      }
      // gcode issue #508
      if (void.class.equals(returnType)) {
        ResultType rt = method.getAnnotation(ResultType.class);
        if (rt != null) {
          returnType = rt.value();
        }
      }
    } else if (resolvedReturnType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) resolvedReturnType;
      Class<?> rawType = (Class<?>) parameterizedType.getRawType();
      if (Collection.class.isAssignableFrom(rawType) || Cursor.class.isAssignableFrom(rawType)) {
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          Type returnTypeParameter = actualTypeArguments[0];
          if (returnTypeParameter instanceof Class<?>) {
            returnType = (Class<?>) returnTypeParameter;
          } else if (returnTypeParameter instanceof ParameterizedType) {
            // (gcode issue #443) actual type can be a also a parameterized type
            returnType = (Class<?>) ((ParameterizedType) returnTypeParameter).getRawType();
          } else if (returnTypeParameter instanceof GenericArrayType) {
            Class<?> componentType = (Class<?>) ((GenericArrayType) returnTypeParameter).getGenericComponentType();
            // (gcode issue #525) support List<byte[]>
            returnType = Array.newInstance(componentType, 0).getClass();
          }
        }
      } else if (method.isAnnotationPresent(MapKey.class) && Map.class.isAssignableFrom(rawType)) {
        // (gcode issue 504) Do not look into Maps if there is not MapKey annotation
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 2) {
          Type returnTypeParameter = actualTypeArguments[1];
          if (returnTypeParameter instanceof Class<?>) {
            returnType = (Class<?>) returnTypeParameter;
          } else if (returnTypeParameter instanceof ParameterizedType) {
            // (gcode issue 443) actual type can be a also a parameterized type
            returnType = (Class<?>) ((ParameterizedType) returnTypeParameter).getRawType();
          }
        }
      } else if (Optional.class.equals(rawType)) {
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        Type returnTypeParameter = actualTypeArguments[0];
        if (returnTypeParameter instanceof Class<?>) {
          returnType = (Class<?>) returnTypeParameter;
        }
      }
    }

    return returnType;
  }

  /**
   * 解析所有@Result信息；一个@Result，构建一个ResultMapping对象；然后放入到resultMappings中
   */
  private void applyResults(Result[] results, Class<?> resultType, List<ResultMapping> resultMappings) {
    /*

    1、解析所有@Result信息；一个@Result，构建一个ResultMapping对象；然后放入到resultMappings中

     */
    for (Result result : results) {

      List<ResultFlag> flags = new ArrayList<>();
      if (result.id()) {
        flags.add(ResultFlag.ID);
      }

      @SuppressWarnings("unchecked")
      Class<? extends TypeHandler<?>> typeHandler = (Class<? extends TypeHandler<?>>)
        ((result.typeHandler() == UnknownTypeHandler.class) ? null : result.typeHandler());
      boolean hasNestedResultMap = hasNestedResultMap(result);

      // ⚠️构建一个ResultMapping对象
      ResultMapping resultMapping = assistant.buildResultMapping/* @Result */(
        resultType,
        nullOrEmpty(result.property()),
        nullOrEmpty(result.column()),
        result.javaType() == void.class ? null : result.javaType(),
        result.jdbcType() == JdbcType.UNDEFINED ? null : result.jdbcType(),
        hasNestedSelect(result) ? nestedSelectId(result) : null,
        // ⚠️获取嵌套的resultMapId
        hasNestedResultMap ? nestedResultMapId(result) : null,
        null,
        hasNestedResultMap ? findColumnPrefix(result) : null,
        typeHandler,
        flags,
        null,
        null,
        isLazy(result));

      // 放入到resultMappings中
      resultMappings.add(resultMapping);
    }
  }

  private String findColumnPrefix(Result result) {
    String columnPrefix = result.one().columnPrefix();
    if (columnPrefix.length() < 1) {
      columnPrefix = result.many().columnPrefix();
    }
    return columnPrefix;
  }

  private String nestedResultMapId(Result result) {
    String resultMapId = result.one().resultMap();
    if (resultMapId.length() < 1) {
      resultMapId = result.many().resultMap();
    }
    if (!resultMapId.contains(".")) {
      resultMapId = type.getName() + "." + resultMapId;
    }
    return resultMapId;
  }

  private boolean hasNestedResultMap(Result result) {
    if (result.one().resultMap().length() > 0 && result.many().resultMap().length() > 0) {
      throw new BuilderException("Cannot use both @One and @Many annotations in the same @Result");
    }
    return result.one().resultMap().length() > 0 || result.many().resultMap().length() > 0;
  }

  private String nestedSelectId(Result result) {
    String nestedSelect = result.one().select();
    if (nestedSelect.length() < 1) {
      nestedSelect = result.many().select();
    }
    if (!nestedSelect.contains(".")) {
      nestedSelect = type.getName() + "." + nestedSelect;
    }
    return nestedSelect;
  }

  private boolean isLazy(Result result) {
    boolean isLazy = configuration.isLazyLoadingEnabled();
    // 存在嵌套查询 && fetchType(获取类型)不是FetchType.DEFAULT
    if (result.one().select().length() > 0 && FetchType.DEFAULT != result.one().fetchType()) {
      // 根据fetchType属性判断是不是懒加载
      isLazy = result.one().fetchType() == FetchType.LAZY;
    }
    // 存在嵌套查询 && fetchType(获取类型)不是FetchType.DEFAULT
    else if (result.many().select().length() > 0 && FetchType.DEFAULT != result.many().fetchType()) {
      // 根据fetchType属性判断是不是懒加载
      isLazy = result.many().fetchType() == FetchType.LAZY;
    }
    return isLazy;
  }

  private boolean hasNestedSelect(Result result) {
    if (result.one().select().length() > 0 && result.many().select().length() > 0) {
      throw new BuilderException("Cannot use both @One and @Many annotations in the same @Result");
    }
    return result.one().select().length() > 0 || result.many().select().length() > 0;
  }

  /**
   * 解析所有@Arg信息；一个@Arg，构建一个ResultMapping对象；然后放入到resultMappings中
   */
  private void applyConstructorArgs(Arg[] args, Class<?> resultType, List<ResultMapping> resultMappings) {
    /*

    1、解析所有@Arg信息，一个@Arg，构建一个ResultMapping对象，然后放入到resultMappings中

     */
    for (Arg arg : args) {
      List<ResultFlag> flags = new ArrayList<>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if (arg.id()) {
        flags.add(ResultFlag.ID);
      }
      @SuppressWarnings("unchecked")
      Class<? extends TypeHandler<?>> typeHandler = (Class<? extends TypeHandler<?>>)
        (arg.typeHandler() == UnknownTypeHandler.class ? null : arg.typeHandler());
      // 构建一个ResultMapping对象
      ResultMapping resultMapping = assistant.buildResultMapping/* @Arg */(
        resultType,
        nullOrEmpty(arg.name()),
        nullOrEmpty(arg.column()),
        arg.javaType() == void.class ? null : arg.javaType(),
        arg.jdbcType() == JdbcType.UNDEFINED ? null : arg.jdbcType(),
        nullOrEmpty(arg.select()),
        nullOrEmpty(arg.resultMap()),
        null,
        nullOrEmpty(arg.columnPrefix()),
        typeHandler,
        flags,
        null,
        null,
        false);
      resultMappings.add(resultMapping);
    }
  }

  private String nullOrEmpty(String value) {
    return value == null || value.trim().length() == 0 ? null : value;
  }

  private KeyGenerator handleSelectKeyAnnotation(SelectKey selectKeyAnnotation, String baseStatementId, Class<?> parameterTypeClass, LanguageDriver languageDriver) {
    String id = baseStatementId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    Class<?> resultTypeClass = selectKeyAnnotation.resultType();
    StatementType statementType = selectKeyAnnotation.statementType();
    String keyProperty = selectKeyAnnotation.keyProperty();
    String keyColumn = selectKeyAnnotation.keyColumn();
    boolean executeBefore = selectKeyAnnotation.before();

    // defaults
    boolean useCache = false;
    KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;
    String databaseId = selectKeyAnnotation.databaseId().isEmpty() ? null : selectKeyAnnotation.databaseId();

    SqlSource sqlSource = buildSqlSource(selectKeyAnnotation, parameterTypeClass, languageDriver, null);
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

    assistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType, fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass, resultSetTypeEnum,
      flushCache, useCache, false,
      keyGenerator, keyProperty, keyColumn, databaseId, languageDriver, null);

    id = assistant.applyCurrentNamespace(id, false);

    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    SelectKeyGenerator answer = new SelectKeyGenerator(keyStatement, executeBefore);
    configuration.addKeyGenerator(id, answer);
    return answer;
  }

  /**
   * 获取@Select、@Update、@Insert、@Delete、@SelectKey里面的sql语句，构建为一个SqlSource对象
   *
   * @param annotation
   * @param parameterType     方法参数
   * @param languageDriver
   * @param method
   * @return
   */
  private SqlSource buildSqlSource(Annotation annotation, Class<?> parameterType, LanguageDriver languageDriver,
                                   Method method) {
    if (annotation instanceof Select) {
      return buildSqlSourceFromStrings(((Select) annotation).value()/* sql语句 */, parameterType, languageDriver);
    } else if (annotation instanceof Update) {
      return buildSqlSourceFromStrings(((Update) annotation).value(), parameterType, languageDriver);
    } else if (annotation instanceof Insert) {
      return buildSqlSourceFromStrings(((Insert) annotation).value(), parameterType, languageDriver);
    } else if (annotation instanceof Delete) {
      return buildSqlSourceFromStrings(((Delete) annotation).value(), parameterType, languageDriver);
    } else if (annotation instanceof SelectKey) {
      return buildSqlSourceFromStrings(((SelectKey) annotation).statement()/* sql语句 */, parameterType, languageDriver);
    }
    return new ProviderSqlSource(assistant.getConfiguration(), annotation, type, method);
  }

  /**
   *
   * @param strings
   * @param parameterTypeClass    方法参数
   * @param languageDriver
   * @return
   */
  private SqlSource buildSqlSourceFromStrings(String[] strings, Class<?> parameterTypeClass,
                                              LanguageDriver languageDriver) {
    return languageDriver.createSqlSource(configuration, String.join(" ", strings).trim(), parameterTypeClass);
  }

  /**
   * 获取方法上的sql注解，然后创建对应的AnnotationWrapper返回，里面存放sql注解
   *
   * @param method         方法
   * @param errorIfNoMatch 错误如果没有匹配，false
   * @param targetTypes    注解（@Select、@SelectProvider）
   * @return
   */
  @SafeVarargs
  private final Optional<AnnotationWrapper> getAnnotationWrapper(Method method, boolean errorIfNoMatch,
                                                                 Class<? extends Annotation>... targetTypes) {
    return getAnnotationWrapper(method, errorIfNoMatch, Arrays.asList(targetTypes));
  }

  /**
   * 获取方法上对应的sql注解，然后创建对应的AnnotationWrapper返回（AnnotationWrapper里面包含该注解，以及注解上的databaseId、以及根据注解类型得到SqlCommandType）；
   *
   * 注意：同一databaseId下，只能存在targetTypes中的某一个注解，否则报错！
   *
   * @param method         方法
   * @param errorIfNoMatch 如果当前方法上不存在targetTypes注解，是否要进行抛错呢？（false）
   * @param targetTypes    注解（@Select、@SelectProvider）
   * @return
   */
  private Optional<AnnotationWrapper> getAnnotationWrapper(Method method, boolean errorIfNoMatch,
                                                           Collection<Class<? extends Annotation>> targetTypes) {
    // 获取当前数据库id
    // 题外：这个一般为null，不会去配置
    String databaseId = configuration.getDatabaseId();

    /*

    1、获取方法上targetTypes类型的注解；
    并为每个注解，创建对应的AnnotationWrapper对象（AnnotationWrapper里面包含该注解，以及注解上的databaseId、以及根据注解类型得到SqlCommandType）；
    然后根据databaseId对AnnotationWrapper进行分组，最终得出key=databaseId、value=AnnotationWrapper的statementAnnotations集合；
    在分组的过程中，会校验，只允许，同一databaseId下，只能存在targetTypes中的一个注解，否则报错！

     */

    // sql语句注解集合
    // key：注解中的databaseId，一般为null
    // value：方法上的注解，所对应的AnnotationWrapper
    // >>> AnnotationWrapper里面存放注解（注意：⚠️并未解析注解，只是存放）
    Map<String, AnnotationWrapper> statementAnnotations = targetTypes.stream()
      // 将多个数组，合并为一个list
      .flatMap(x ->
        // 获取方法上所有对应类型的注解，返回的是一个数组，因为可以定义多个，然后把数组变为stream流
        Arrays.stream(method.getAnnotationsByType(x))
      )
      // ⚠️为方法上的每一个注解，创建对应的AnnotationWrapper。AnnotationWrapper里面存放注解（注意：⚠️并未解析注解，只是存放）
      .map(AnnotationWrapper::new)
      .collect(Collectors.toMap(AnnotationWrapper::getDatabaseId, x -> x, (existing, duplicate) -> {
        // 注意：⚠️这里是databaseId，作为当前这个方法上面排斥定义多个注解的（虽然人为可以定义多个，但是不允许定义多个），也就是说同一databaseId下，只能存在targetTypes中的某一个注解
        // 例如：1、一个方法上面定义了2个@Select注解，它们的databaseId相同，则报错（如果都没定义databaseId，则都为null，也代表databaseId相同，则都报错）；
        // >>> 2、或者说定义了一个个@Select、又定义了一个@SelectProvider，它们的databaseId相同，则报错

        // 注意：⚠️如果想要它们不排斥，那么可以设置不同的databaseId，这也从侧面反应，一个接口方法上面可以定义操作不同数据库的sql语句

        // 在“%s”上检测到冲突的注解“%s”和“%s”。
        throw new BuilderException(String.format("Detected conflicting annotations '%s' and '%s' on '%s'.",
          existing.getAnnotation(), duplicate.getAnnotation(),
          method.getDeclaringClass().getName() + "." + method.getName()));
      }));

    /*

    2、从statementAnnotations集合中，获取当前databaseId，所对应的唯一AnnotationWrapper

    ⚠️从这里可以看出，最终只会获取一个注解对应的AnnotationWrapper，也就是说在一个databaseId下，只允许书写targetTypes中的一个注解！

    */

    AnnotationWrapper annotationWrapper = null;
    if (databaseId != null) {
      annotationWrapper = statementAnnotations.get(databaseId);
    }
    if (annotationWrapper == null) {
      annotationWrapper = statementAnnotations.get("");
    }

    if (errorIfNoMatch/* 如果当前方法上不存在targetTypes注解，是否要进行抛错呢？ */ && annotationWrapper == null && !statementAnnotations.isEmpty()) {
      // Annotations exist, but there is no matching one for the specified databaseId
      // 上面的翻译：注解存在，但指定的databaseId没有匹配的注解
      throw new BuilderException(
        String.format(
          "Could not find a statement annotation that correspond a current database or default statement on method '%s.%s'. Current database id is [%s].",
          method.getDeclaringClass().getName(), method.getName(), databaseId));
    }

    return Optional.ofNullable(annotationWrapper);
  }

  private class AnnotationWrapper {

    // 方法上的注解
    private final Annotation annotation;
    // 数据库id
    private final String databaseId;
    // sql命名类型
    private final SqlCommandType sqlCommandType;

    AnnotationWrapper(Annotation annotation) {
      super();
      // 注解
      this.annotation = annotation;
      // @Select
      if (annotation instanceof Select) {
        databaseId = ((Select) annotation).databaseId();
        sqlCommandType = SqlCommandType.SELECT;
      }
      // @Update
      else if (annotation instanceof Update) {
        databaseId = ((Update) annotation).databaseId();
        sqlCommandType = SqlCommandType.UPDATE;
      }
      // @Insert
      else if (annotation instanceof Insert) {
        databaseId = ((Insert) annotation).databaseId();
        sqlCommandType = SqlCommandType.INSERT;
      }
      // @Delete
      else if (annotation instanceof Delete) {
        databaseId = ((Delete) annotation).databaseId();
        sqlCommandType = SqlCommandType.DELETE;
      }
      // @SelectProvider
      else if (annotation instanceof SelectProvider) {
        databaseId = ((SelectProvider) annotation).databaseId();
        sqlCommandType = SqlCommandType.SELECT;
      }
      // @UpdateProvider
      else if (annotation instanceof UpdateProvider) {
        databaseId = ((UpdateProvider) annotation).databaseId();
        sqlCommandType = SqlCommandType.UPDATE;
      }
      // @InsertProvider
      else if (annotation instanceof InsertProvider) {
        databaseId = ((InsertProvider) annotation).databaseId();
        sqlCommandType = SqlCommandType.INSERT;
      }
      // @DeleteProvider
      else if (annotation instanceof DeleteProvider) {
        databaseId = ((DeleteProvider) annotation).databaseId();
        sqlCommandType = SqlCommandType.DELETE;
      } else {
        sqlCommandType = SqlCommandType.UNKNOWN;
        // @Options
        if (annotation instanceof Options) {
          databaseId = ((Options) annotation).databaseId();
        }
        // @SelectKey
        else if (annotation instanceof SelectKey) {
          databaseId = ((SelectKey) annotation).databaseId();
        } else {
          databaseId = "";
        }
      }
    }

    Annotation getAnnotation() {
      return annotation;
    }

    SqlCommandType getSqlCommandType() {
      return sqlCommandType;
    }

    String getDatabaseId() {
      return databaseId;
    }
  }
}
