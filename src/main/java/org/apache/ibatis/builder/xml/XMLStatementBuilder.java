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

import java.util.List;
import java.util.Locale;

import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * XML语句构建器（建造者模式，继承BaseBuilder）
 *
 * @author Clinton Begin
 */
public class XMLStatementBuilder extends BaseBuilder {

  // 映射器构建助手（mapper构建助手）
  private final MapperBuilderAssistant builderAssistant;
  // <select><insert><update><delete>标签中的一种
  private final XNode context;
  private final String requiredDatabaseId;

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context) {
    this(configuration, builderAssistant, context, null);
  }

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context, String databaseId) {
    super(configuration);
    this.builderAssistant = builderAssistant;
    // <select><insert><update><delete>标签中的一种
    this.context = context;
    this.requiredDatabaseId = databaseId;
  }

  /**
   * 解析<select><insert><update><delete>标签信息，
   * 得到一个MappedStatement对象，然后放入configuration的映射语句集合中（mappedStatements）
   *
   * 参考：
   * <select id="findAll" databaseId="" resultMap="userMap" resultType="" useCache=""
   *         fetchSize="" flushCache="false" lang="" parameterMap="" parameterType=""
   *         resultOrdered="" resultSets="" resultSetType="" statementType="" timeout="">
   *     select *
   *     from user
   * </select>
   *
   * <update id="updateById" databaseId="" timeout="" statementType="" parameterType=""
   *         parameterMap="" lang="" flushCache="" keyColumn="" keyProperty="" useGeneratedKeys="">
   *     update user
   *     set username='zhangsan'
   *     where id = #{id}
   * </update>
   *
   * <delete id="" databaseId="" flushCache="false" lang="" parameterMap="" parameterType="" statementType="CALLABLE"
   *         timeout="">
   * </delete>
   *
   * <insert id="" databaseId="" timeout="" statementType="" parameterType="" parameterMap="" lang="" flushCache=""
   *         useGeneratedKeys="" keyProperty="" keyColumn="">
   * </insert>
   *
   */
  public void parseStatementNode() {
    /**
     * 例如：
     *   <insert id="insert">
     *     INSERT INTO `user` (, `username`, `password`)
     *     VALUES (#{username}, #{password});
     *   </insert>
     *
     * 那么id = insert
     */
    // 获取<select><insert><update><delete>标签的id，也就是接口方法的名称
    String id = context.getStringAttribute("id");
    // 获取<select><insert><update><delete>标签的databaseId
    String databaseId = context.getStringAttribute("databaseId");

    // 如果databaseId不匹配，退出
    if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
      return;
    }

    // 获取节点名称(select|insert|update|delete)
    // 题外：根据节点名称，可以得出命令类型
    String nodeName = context.getNode().getNodeName();

    /* 根据节点名称，得出sql命令类型 */
    SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));

    // 判断是不是SELECT
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    // flushCache属性：是否要刷新二级缓存（默认值：当前是select操作的话，默认值为false，也就是不刷新缓存；当前操作不是select操作的话，则默认为true，代表要刷新缓存）
    boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);

    // 是否要缓存select结果（注意：⚠️也只有<select>标签，才有useCache属性）
    // >>> 如果存在"useCache"，则使用"useCache"的结果作为是否要使用缓存；
    // >>> 如果不存在，则使用isSelect的结果作为是否要使用缓存；
    boolean useCache = context.getBooleanAttribute("useCache", isSelect);

    // 仅针对嵌套结果 select 语句适用：
    // 如果为true，就是假设包含了嵌套结果集或是分组了，这样的话，当返回一个主结果行的时候，就不会发生有对前面结果集的引用的情况。
    // 这就使得在获取嵌套的结果集的时候不至于导致内存不够用。默认值：false。
    boolean resultOrdered = context.getBooleanAttribute("resultOrdered"/* 结果有序 */, false);

    /* 解析<include>SQL片段 */

    // Include Fragments before parsing —— 解析前包含片段
    // 解析之前先解析<include>SQL片段
    // 看一下有没有包含其它的<include>节点，有的话就加载
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    // 应用<include>标签
    // <include>标签所引用的是<sql>代码片段
    includeParser.applyIncludes(context.getNode());

    /* 入参的参数类型 */
    String parameterType = context.getStringAttribute("parameterType");
    Class<?> parameterTypeClass = resolveClass(parameterType);

    /* 获取语言驱动器 */
    // 脚本语言，mybatis3.2的新功能
    String lang = context.getStringAttribute("lang");
    // 获取语言驱动器
    LanguageDriver langDriver = getLanguageDriver(lang);

    // Parse selectKey after includes and remove them. —— 在包含后解析selectKey并删除它们。

    /*

    解析<selectKey>标签

    主要干了两件事：

    1、构建<selectKey>标签中sql语句，为一个MappedStatement；
    然后添加到Configuration中的映射语句集合中(mappedStatements)

    2、构建<selectKey>标签为一个KeyGenerator，里面保存了标签中sql语句所对应的MappedStatement对象，
    然后添加到Configuration中的key生成器集合中(keyGenerators)

     */
    // 解析<selectKey>标签
    processSelectKeyNodes(id, parameterTypeClass, langDriver);

    /*

    确定KeyGenerator

    如果存在<selectKey>标签，则使用<selectKey>标签，构建的KeyGenerator；
    否则尝试，使用<insert>标签上的useGeneratedKeys属性，构建的KeyGenerator

     */

    // Parse the SQL (pre: <selectKey> and <include> were parsed and removed) —— 解析SQL（pre: <selectKey> 和 <include> 被解析并移除）
    KeyGenerator keyGenerator;
    /**
     * 例如：
     * <mapper namespace="com.msb.mybatis_02.dao.UserDao">
     *     <select id="getUser" resultType="com.msb.mybatis_02.bean.User">
     *         select * from user where id=#{id}
     *     </select>
     * </mapper>
     */
    // keyStatementId = getUser!selectKey
    String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX/* !selectKey */;
    // 引用命名空间：拼接上当前dao.xml的命名空间，形成新的keyStatementId
    // keyStatementId = com.msb.mybatis_02.dao.UserDao.getUser!selectKey
    keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);

    // 如果标签下，存在<selectKey>标签，则使用<selectKey>标签，构建的KeyGenerator
    if (configuration.hasKeyGenerator(keyStatementId)) {
      keyGenerator = configuration.getKeyGenerator(keyStatementId);
    }
    // 如果标签下，不存在<SelectKey>标签，则使用<insert>标签上的useGeneratedKeys属性，构建的KeyGenerator
    else {
      // 如果标签中存在useGeneratedKeys属性值，就采用标签中的useGeneratedKeys属性值；如果不存在，则使用mybatis-config.xml中全局的useGeneratedKeys配置
      // 只有useGeneratedKeys为true，并且是insert操作，才使用keyGenerator
      keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
          configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
          ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
    }

    /* 提取<insert>、<update>、<delete>、<select>标签中的sql语句，构建成SqlSource */
    SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);

    /* 确定执行sql的对象类型，例如：Statement、PreparedStatement，默认为PreparedStatement */
    // 题外：默认情况下，为了避免sql注入，选择的是PREPARED = PreparedStatement
    StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));

    // 暗示驱动程序每次批量返回的结果行数
    Integer fetchSize = context.getIntAttribute("fetchSize");
    // 超时时间
    Integer timeout = context.getIntAttribute("timeout");
    // 引用外部参数集合parameterMap（已废弃）
    // 例如：<select parameterMap="">中的parameterMap属性
    String parameterMap = context.getStringAttribute("parameterMap");

    /* resultType */
    // 结果类型
    String resultType = context.getStringAttribute("resultType");
    Class<?> resultTypeClass = resolveClass(resultType);

    /* resultMap */
    /**
     * 例如：
     * <resultMap id="userMap" type="com.msb.mybatis_02.bean.User">
     *   <id column="id" property="id"/>
     *   <result column="username" property="username"/>
     *   <result column="password" property="password"/>
     * </resultMap>
     *
     * <select id="getUserById" resultMap="userMap">
     *     select * from user where id =1;
     * </select>
     */
    // <select>标签中的resultMap属性值（高级功能）
    String resultMap = context.getStringAttribute("resultMap");

    /* 确定结果集类型，FORWARD_ONLY|SCROLL_SENSITIVE|SCROLL_INSENSITIVE 中的一种 */
    String resultSetType = context.getStringAttribute("resultSetType");
    ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);
    if (resultSetTypeEnum == null) {
      resultSetTypeEnum = configuration.getDefaultResultSetType();
    }

    // (仅对 insert 有用) 标记一个属性, MyBatis 会通过 getGeneratedKeys 或者通过 insert 语句的 selectKey 子元素设置它的值
    String keyProperty = context.getStringAttribute("keyProperty");

    // (仅对 insert 有用) 标记一个属性, MyBatis 会通过 getGeneratedKeys 或者通过 insert 语句的 selectKey 子元素设置它的值
    String keyColumn = context.getStringAttribute("keyColumn");

    /* resultSets */
    String resultSets = context.getStringAttribute("resultSets");

    /* 通过<insert>、<delete>、<update>、<select>等标签信息，构建一个MappedStatement对象，添加到configuration的映射语句集合中（mappedStatements） */
    builderAssistant.addMappedStatement(id, sqlSource/* sql语句 */, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered,
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets);
  }

  /**
   * 解析<selectKey>标签
   *
   * 主要干了两件事：
   *
   * 1、构建<selectKey>标签中sql语句，为一个MappedStatement；
   * 然后添加到Configuration中的映射语句集合中(mappedStatements)
   *
   * 2、构建<selectKey>标签为一个KeyGenerator，里面保存了标签中sql语句所对应的MappedStatement对象，
   * 然后添加到Configuration中的key生成器集合中(keyGenerators)
   *
   * 参考：
   * <insert id="insert">
   *   <selectKey keyProperty="id" resultType="int" order="BEFORE">
   *     select CAST(RANDOM()*1000000 as INTEGER) a from SYSIBM.SYSDUMMY1
   *   </selectKey>
   *   INSERT INTO `user` (, `username`, `password`)
   *   VALUES (#{username}, #{password});
   * </insert>
   *
   * @param id                          <select><insert><update><delete>标签的id属性
   * @param parameterTypeClass          <select><insert><update><delete>标签的parameterType属性
   * @param langDriver                  <select><insert><update><delete>标签的lang属性
   */
  private void processSelectKeyNodes(String id, Class<?> parameterTypeClass, LanguageDriver langDriver) {
    List<XNode> selectKeyNodes = context.evalNodes("selectKey");

    if (configuration.getDatabaseId() != null) {
      // ⚠️解析<selectKey>标签
      parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, configuration.getDatabaseId());
    }
    // ⚠️解析<selectKey>标签
    parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, null);

    removeSelectKeyNodes(selectKeyNodes);
  }

  /**
   * 解析<selectKey>标签
   *
   * 主要干了两件事：
   *
   * 1、构建<selectKey>标签中sql语句，为一个MappedStatement；
   * 然后添加到Configuration中的映射语句集合中(mappedStatements)
   *
   * 2、构建<selectKey>标签为一个KeyGenerator，里面保存了标签中sql语句所对应的MappedStatement对象，
   * 然后添加到Configuration中的key生成器集合中(keyGenerators)
   *
   * 参考：
   * <insert id="insert">
   *   <selectKey keyProperty="id" resultType="int" order="BEFORE">
   *     select CAST(RANDOM()*1000000 as INTEGER) a from SYSIBM.SYSDUMMY1
   *   </selectKey>
   *   INSERT INTO `user` (, `username`, `password`)
   *   VALUES (#{username}, #{password});
   * </insert>
   *
   * @param parentId                            <select><insert><update><delete>标签的id属性
   * @param list
   * @param parameterTypeClass                  <select><insert><update><delete>标签的parameterType属性
   * @param langDriver                          <select><insert><update><delete>标签的lang属性
   * @param skRequiredDatabaseId
   */
  private void parseSelectKeyNodes(String parentId, List<XNode> list, Class<?> parameterTypeClass, LanguageDriver langDriver, String skRequiredDatabaseId) {
    for (XNode nodeToHandle : list) {
      String id = parentId + SelectKeyGenerator.SELECT_KEY_SUFFIX/* !selectKey */;
      String databaseId = nodeToHandle.getStringAttribute("databaseId");
      if (databaseIdMatchesCurrent(id, databaseId, skRequiredDatabaseId)) {
        // ⚠️解析<selectKey>标签
        parseSelectKeyNode(id, nodeToHandle, parameterTypeClass, langDriver, databaseId);
      }
    }
  }

  /**
   * ⚠️解析<selectKey>标签
   *
   * 主要干了两件事：
   *
   * 1、构建<selectKey>标签中sql语句，为一个MappedStatement；
   * 然后添加到Configuration中的映射语句集合中(mappedStatements)
   *
   * 2、构建<selectKey>标签为一个KeyGenerator，里面保存了标签中sql语句所对应的MappedStatement对象，
   * 然后添加到Configuration中的key生成器集合中(keyGenerators)
   *
   * 参考：
   * <insert id="insert">
   *   <selectKey keyProperty="id" resultType="int" order="BEFORE">
   *     select CAST(RANDOM()*1000000 as INTEGER) a from SYSIBM.SYSDUMMY1
   *   </selectKey>
   *   INSERT INTO `user` (, `username`, `password`)
   *   VALUES (#{username}, #{password});
   * </insert>
   *
   * @param id                        insert!selectKey
   * @param nodeToHandle              <selectKey>标签
   * @param parameterTypeClass        <select><insert><update><delete>标签的parameterType属性
   * @param langDriver
   * @param databaseId
   */
  private void parseSelectKeyNode(String id, XNode nodeToHandle, Class<?> parameterTypeClass, LanguageDriver langDriver, String databaseId) {

    String resultType = nodeToHandle.getStringAttribute("resultType");
    Class<?> resultTypeClass = resolveClass(resultType);
    StatementType statementType = StatementType.valueOf(nodeToHandle.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    String keyProperty = nodeToHandle.getStringAttribute("keyProperty");
    String keyColumn = nodeToHandle.getStringAttribute("keyColumn");
    boolean executeBefore = "BEFORE".equals(nodeToHandle.getStringAttribute("order", "AFTER"));

    // defaults
    boolean useCache = false;
    boolean resultOrdered = false;
    KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;

    // ⚠️解析成SqlSource， 一般是DynamicSqlSource、否则就是RawSqlSource
    SqlSource sqlSource = langDriver.createSqlSource(configuration, nodeToHandle/* <selectKey>标签 */, parameterTypeClass);
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

    /*

    1、构建<selectKey>标签中sql语句，为一个MappedStatement；
    然后添加到Configuration中的映射语句集合中(mappedStatements)

    */

    /**
     * 增加<selectKey>标签中的映射语句到Configuration：
     * 构建<selectKey>标签中的sql语句为对应的MappedStatement，作为value；
     * 然后将<selectKey>标签的外层标签，例如：<insert>标签的id属性，作为id标识，拼接上!selectKey，拼接上命名空间，得到一个"sql语句标识"，作为key，
     * 也就是：将"sql语句标识"与对应的"sql语句"映射起来，存入configuration中的映射语句集合中
     * >>> key（String）：sql标签id，例如：com.msb.mybatis_02.dao.UserDao.insert!selectKey
     * >>> value（MappedStatement）：sql语句
     * 题外：id = insert!selectKey
     */
    // ⚠️增加映射语句到Configuration —— 增加<selectKey>标签中的映射语句到Configuration
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered,
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, null);

    // 为id添加上命名空间前缀，例如：com.msb.mybatis_02.dao.UserDao.insert!selectKey
    id = builderAssistant.applyCurrentNamespace(id, false);

    // 获取<selectKey>标签中sql语句，对应的MappedStatement
    // 题外：因为上面刚刚builderAssistant.addMappedStatement()中，把当前<selectKey>标签中的sql语句对应的MappedStatement，添加到了configuration.mappedStatements中，所以才能获取得到！
    MappedStatement keyStatement = configuration.getMappedStatement(id, false);

    /*

    2、构建<selectKey>标签为一个KeyGenerator，里面保存了标签中sql语句所对应的MappedStatement对象，
    然后添加到Configuration中的key生成器集合中(keyGenerators)

    */

    // ⚠️添加<selectKey>标签对应的KeyGenerator到configuration.keyGenerators中
    configuration.addKeyGenerator(id, new SelectKeyGenerator(keyStatement, executeBefore));
  }

  private void removeSelectKeyNodes(List<XNode> selectKeyNodes) {
    for (XNode nodeToHandle : selectKeyNodes) {
      nodeToHandle.getParent().getNode().removeChild(nodeToHandle.getNode());
    }
  }

  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    if (databaseId != null) {
      return false;
    }
    id = builderAssistant.applyCurrentNamespace(id, false);
    if (!this.configuration.hasStatement(id, false)) {
      return true;
    }
    // skip this statement if there is a previous one with a not null databaseId
    MappedStatement previous = this.configuration.getMappedStatement(id, false); // issue #2
    return previous.getDatabaseId() == null;
  }

  // 获取语言驱动
  private LanguageDriver getLanguageDriver(String lang) {
    Class<? extends LanguageDriver> langClass = null;
    if (lang != null) {
      langClass = resolveClass(lang);
    }
    return configuration.getLanguageDriver(langClass);
  }

}
