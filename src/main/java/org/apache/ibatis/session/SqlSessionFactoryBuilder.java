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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;

/**
 * Builds {@link SqlSession} instances.
 *
 * @author Clinton Begin
 */
public class SqlSessionFactoryBuilder {

  public SqlSessionFactory build(Reader reader) {
    return build(reader, null, null);
  }

  public SqlSessionFactory build(Reader reader, String environment) {
    return build(reader, environment, null);
  }

  public SqlSessionFactory build(Reader reader, Properties properties) {
    return build(reader, null, properties);
  }

  public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
    try {
      XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);
      return build(parser.parse());
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
      	if (reader != null) {
      	  reader.close();
      	}
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

  public SqlSessionFactory build(InputStream inputStream) {
    return build(inputStream, null, null);
  }

  public SqlSessionFactory build(InputStream inputStream, String environment) {
    return build(inputStream, environment, null);
  }

  public SqlSessionFactory build(InputStream inputStream, Properties properties) {
    return build(inputStream, null, properties);
  }

  public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
    try {
      /* 1、加载配置文件到内存中并生成一个document对象，同时创建Configuration对象 */
      /**
       * 1、XMLConfigBuilder：配置文件的Builder对象
       * （1）里面包含一个XPathParser对象，XPathParser对象里面包含一个document，这个document是加载配置文件形成的document
       * （2）包含一个Configuration对象
       */
      // 加载配置文件
      // 前戏/准备工作：加载配置文件到内存中并生成一个document对象，同时创建Configuration对象
      XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);

      /* 2、解析配置文件中的信息(document对象中的信息)，以及会解析配置文件中引入的mapper文件，将解析到的信息都填充到Configuration对象里面去 */
      /**
       * 1、parser.parse()：执行完这一步，就把mybatis-config.xml文件中的内容都解析完了，把所有的属性值都放入了Configuration对象里面去了，然后返回这个Configuration对象
       * 2、build(parser.parse())：创建SqlSessionFactory对象，将Configuration对象保存在里面
       */
      Configuration parse = parser.parse();

      /*

      3、创建SqlSessionFactory对象（DefaultSqlSessionFactory），将Configuration对象保存在里面；
      后续SqlSessionFactory对象可依靠Configuration对象里面的信息，创建SqlSession对象

      */
      return build(parse);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
      	if (inputStream != null) {
      	  inputStream.close();
      	}
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

  /**
   * 创建DefaultSqlSessionFactory，将Configuration对象保存在里面
   */
  public SqlSessionFactory build(Configuration config) {
    return new DefaultSqlSessionFactory(config);
  }

}
