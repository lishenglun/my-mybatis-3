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
package org.apache.ibatis.mapping;

import java.util.Collections;
import java.util.Map;

import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class Discriminator {

  /**
   * 例如：
   *
   * <discriminator javaType="String" column="sex">
   *   <!-- 男性 -->
   *   <case value="1" resultMap="HealthReportMaleResultMap"/>
   *   <!-- 女性 -->
   *   <case value="0" resultMap="HealthReportFemale"/>
   * </discriminator>
   *
   * 其中：
   *
   * <discriminator javaType="String" column="sex">标签信息构成ResultMapping
   * <case>标签信息构成discriminatorMap
   *
   * 题外：<discriminator>标签中只能定义<case>这一个标签！
   */
  // 鉴别器标签的信息
  private ResultMapping resultMapping;
  // 鉴别器子标签的信息
  private Map<String, String> discriminatorMap;

  Discriminator() {
  }

  public static class Builder {
    private Discriminator discriminator = new Discriminator();

    public Builder(Configuration configuration, ResultMapping resultMapping, Map<String, String> discriminatorMap) {
      discriminator.resultMapping = resultMapping;
      discriminator.discriminatorMap = discriminatorMap;
    }

    public Discriminator build() {
      assert discriminator.resultMapping != null;
      assert discriminator.discriminatorMap != null;
      assert !discriminator.discriminatorMap.isEmpty();
      //lock down map
      discriminator.discriminatorMap = Collections.unmodifiableMap(discriminator.discriminatorMap);
      return discriminator;
    }
  }

  public ResultMapping getResultMapping() {
    return resultMapping;
  }

  public Map<String, String> getDiscriminatorMap() {
    return discriminatorMap;
  }

  /**
   * 根据要鉴别的列的值，获取对应的resultMapId
   *
   * @param s
   * @return
   */
  public String getMapIdFor(String s) {
    return discriminatorMap.get(s);
  }

}
