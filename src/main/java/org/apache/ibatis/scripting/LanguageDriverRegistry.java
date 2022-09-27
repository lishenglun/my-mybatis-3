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
package org.apache.ibatis.scripting;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.util.MapUtil;

/**
 * @author Frank D. Martinez [mnesarco]
 */
public class LanguageDriverRegistry {

  // 语言驱动器集合
  // key：语言驱动器类型
  // value：语言驱动器实例
  private final Map<Class<? extends LanguageDriver>, LanguageDriver> LANGUAGE_DRIVER_MAP = new HashMap<>();

  private Class<? extends LanguageDriver> defaultDriverClass;

  /**
   * 注册语言驱动器
   * （1）如果"语言驱动器集合"中不存在该类型的语言驱动器，则实例化对应类型的语言驱动器，并且存放"语言驱动器类型"和"语言驱动器实例"之间的对应关系
   * （2）如果存在，则什么事情都不做
   *
   * 题外：在实例化Configuration的构造方法中，就注入了2个语言驱动器
   *
   * @param cls     语言驱动器类型
   */
  public void register(Class<? extends LanguageDriver> cls) {
    if (cls == null) {
      throw new IllegalArgumentException("null is not a valid Language Driver");
    }
    // 注册语言驱动器
    // （1）如果"语言驱动器集合"中不存在该类型的语言驱动器，则实例化对应类型的语言驱动器，并且存放，语言驱动器类型和语言驱动器实例之间的对应关系
    // （2）如果存在，则什么事情都不做
    MapUtil.computeIfAbsent(LANGUAGE_DRIVER_MAP, cls/* 语言驱动器类型 */, k -> {
      try {
        /* 实例化语言驱动器 */
        LanguageDriver languageDriver = k.getDeclaredConstructor().newInstance();
        return languageDriver;
      } catch (Exception ex) {
        throw new ScriptingException("Failed to load language driver for " + cls.getName(), ex);
      }
    });
  }

  public void register(LanguageDriver instance) {
    if (instance == null) {
      throw new IllegalArgumentException("null is not a valid Language Driver");
    }
    Class<? extends LanguageDriver> cls = instance.getClass();
    if (!LANGUAGE_DRIVER_MAP.containsKey(cls)) {
      LANGUAGE_DRIVER_MAP.put(cls, instance);
    }
  }

  public LanguageDriver getDriver(Class<? extends LanguageDriver> cls) {
    return LANGUAGE_DRIVER_MAP.get(cls);
  }

  public LanguageDriver getDefaultDriver() {
    return getDriver(getDefaultDriverClass());
  }

  public Class<? extends LanguageDriver> getDefaultDriverClass() {
    return defaultDriverClass;
  }

  public void setDefaultDriverClass(Class<? extends LanguageDriver> defaultDriverClass) {
    register(defaultDriverClass);
    this.defaultDriverClass = defaultDriverClass;
  }

}
