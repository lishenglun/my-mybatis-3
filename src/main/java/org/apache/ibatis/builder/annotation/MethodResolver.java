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

import java.lang.reflect.Method;

/**
 * @author Eduardo Macarron
 */
public class MethodResolver {
  private final MapperAnnotationBuilder annotationBuilder;
  private final Method method;

  public MethodResolver(MapperAnnotationBuilder annotationBuilder, Method method) {
    this.annotationBuilder = annotationBuilder;
    this.method = method;
  }

  /**
   *
   *（1）解析方法上的@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider注解，
   *（2）然后用注解里面的信息(比如：sql语句、参数类型，resultMap集合（之前解析好的，这里通过resultMapId获取到）、sql命令类型、执行sql的对象类型，等信息)，创建MappedStatement对象，
   *（3）放入到Configuration.mappedStatements里面去
   *
   * 简单概括：解析方法上的@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider注解，然后用注解里面的信息，
   * 构建MappedStatement对象，放入到Configuration.mappedStatements里面去
   *
   * 注意：同一个databaseId下，只允许存在@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider中的一个注解
   */
  public void resolve() {
    /*

    （1）解析方法上的@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider注解，
    （2）然后用注解里面的信息(比如：sql语句、参数类型，resultMap集合（之前解析好的，这里通过resultMapId获取到）、sql命令类型、执行sql的对象类型，等信息)，创建MappedStatement对象，
    （3）放入到Configuration.mappedStatements里面去

    注意：同一个databaseId下，只允许存在@Select、@Update、@Insert、@Delete、@SelectProvider、@UpdateProvider、@InsertProvider、@DeleteProvider中的一个注解

     */
    annotationBuilder.parseStatement(method);
  }

}
