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
package org.apache.ibatis.type;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.JapaneseDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;

/**
 * 类型处理器注册机
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public final class TypeHandlerRegistry {

  // jdbc类型处理器集合：记录JdbcType与TypeHandler之间的对应关系，其中JdbcType是一个枚举类型，它对应具体的JDBC类型（该集合主要用于从结果集读取数据时，将数据从Jdbc类型转换成Java类型）
  // key：Jdbc类型
  // value：TypeHandler（Jdbc类型对应的类型处理器）
  private final Map<JdbcType, TypeHandler<?>> jdbcTypeHandlerMap = new EnumMap<>(JdbcType.class);

  // java类型处理器集合：记录了Java类型向指定JdbcType转换时，需要使用TypeHandler对象（java类型向jdbc类型转换的时候，需要的TypeHandler对象）
  // key：java类型
  // value：jdbc类型处理器集合
  // >>> key：jdbc类型
  // >>> value：TypeHandler
  private final Map<Type, Map<JdbcType, TypeHandler<?>>> typeHandlerMap = new ConcurrentHashMap<>();

  private final TypeHandler<Object> unknownTypeHandler;

  // 所有类型处理器集合
  // 记录了全部TypeHandler的类型以及该类型相关的TypeHandler对象
  // key：TypeHandler Class
  // value：TypeHandler对象
  private final Map<Class<?>, TypeHandler<?>> allTypeHandlersMap = new HashMap<>();

  // 空TypeHandler集合的标识
  private static final Map<JdbcType, TypeHandler<?>> NULL_TYPE_HANDLER_MAP = Collections.emptyMap();

  private Class<? extends TypeHandler> defaultEnumTypeHandler = EnumTypeHandler.class;

  /**
   * The default constructor.
   */
  public TypeHandlerRegistry() {
    this(new Configuration());
  }

  /**
   * The constructor that pass the MyBatis configuration.
   *
   * @param configuration a MyBatis configuration
   * @since 3.5.4
   */
  public TypeHandlerRegistry(Configuration configuration) {
    /*

     注册一堆类型处理器，因为涉及到数据库的表和java实体类的映射，数据库的数据类型和java当中的类型不一样，不一样，所以要进行类型转换操作

     题外：spring当中也有类型处理器，TypeConverter

     */

    //构造函数里注册系统内置的类型处理器
    //以下是为多个类型注册到同一个handler
    this.unknownTypeHandler = new UnknownTypeHandler(configuration);

    // 放入typeHandlerMap、allTypeHandlersMap
    register(Boolean.class, new BooleanTypeHandler());
    register(boolean.class, new BooleanTypeHandler());
    // 放入jdbcTypeHandlerMap
    register(JdbcType.BOOLEAN, new BooleanTypeHandler());
    register(JdbcType.BIT, new BooleanTypeHandler());

    // 放入typeHandlerMap、allTypeHandlersMap
    register(Byte.class, new ByteTypeHandler());
    register(byte.class, new ByteTypeHandler());
    // 放入jdbcTypeHandlerMap
    register(JdbcType.TINYINT, new ByteTypeHandler());

    // 放入typeHandlerMap、allTypeHandlersMap
    register(Short.class, new ShortTypeHandler());
    register(short.class, new ShortTypeHandler());
    // 放入jdbcTypeHandlerMap
    register(JdbcType.SMALLINT, new ShortTypeHandler());

    // 放入typeHandlerMap、allTypeHandlersMap
    register(Integer.class, new IntegerTypeHandler());
    register(int.class, new IntegerTypeHandler());
    // 放入jdbcTypeHandlerMap
    register(JdbcType.INTEGER, new IntegerTypeHandler());

    // 放入typeHandlerMap、allTypeHandlersMap
    register(Long.class, new LongTypeHandler());
    register(long.class, new LongTypeHandler());

    register(Float.class, new FloatTypeHandler());
    register(float.class, new FloatTypeHandler());
    // 放入jdbcTypeHandlerMap
    register(JdbcType.FLOAT, new FloatTypeHandler());

    // 放入jdbcTypeHandlerMap
    register(Double.class, new DoubleTypeHandler());
    register(double.class, new DoubleTypeHandler());
    // 放入jdbcTypeHandlerMap
    register(JdbcType.DOUBLE, new DoubleTypeHandler());

    // 放入jdbcTypeHandlerMap
    register(Reader.class, new ClobReaderTypeHandler());
    //以下是为同一个类型的多种变种注册到多个不同的handler
    register(String.class, new StringTypeHandler());
    // StringTypeHandler能够将数据从String类型转换成null类型，所以向typeHandlerMap集合注册该对象，并向allTypeHandlersMap集合注册StringTypeHandler
    register(String.class, JdbcType.CHAR, new StringTypeHandler());
    register(String.class, JdbcType.CLOB, new ClobTypeHandler());
    register(String.class, JdbcType.VARCHAR, new StringTypeHandler());
    register(String.class, JdbcType.LONGVARCHAR, new StringTypeHandler());
    // NStringTypeHandler能够将数据从String类型转换成NVARCHAR，所以向typeHandlerMap集合注册该对象，并向allTypeHandlersMap集合注册NStringTypeHandler
    register(String.class, JdbcType.NVARCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCLOB, new NClobTypeHandler());
    register(JdbcType.CHAR, new StringTypeHandler());
    register(JdbcType.VARCHAR, new StringTypeHandler());
    register(JdbcType.CLOB, new ClobTypeHandler());
    register(JdbcType.LONGVARCHAR, new StringTypeHandler());
    register(JdbcType.NVARCHAR, new NStringTypeHandler());
    register(JdbcType.NCHAR, new NStringTypeHandler());
    register(JdbcType.NCLOB, new NClobTypeHandler());

    register(Object.class, JdbcType.ARRAY, new ArrayTypeHandler());
    register(JdbcType.ARRAY, new ArrayTypeHandler());

    register(BigInteger.class, new BigIntegerTypeHandler());
    register(JdbcType.BIGINT, new LongTypeHandler());

    register(BigDecimal.class, new BigDecimalTypeHandler());
    register(JdbcType.REAL, new BigDecimalTypeHandler());
    register(JdbcType.DECIMAL, new BigDecimalTypeHandler());
    register(JdbcType.NUMERIC, new BigDecimalTypeHandler());

    register(InputStream.class, new BlobInputStreamTypeHandler());
    register(Byte[].class, new ByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.BLOB, new BlobByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.LONGVARBINARY, new BlobByteObjectArrayTypeHandler());
    register(byte[].class, new ByteArrayTypeHandler());
    register(byte[].class, JdbcType.BLOB, new BlobTypeHandler());
    register(byte[].class, JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.BLOB, new BlobTypeHandler());

    register(Object.class, unknownTypeHandler);
    register(Object.class, JdbcType.OTHER, unknownTypeHandler);
    register(JdbcType.OTHER, unknownTypeHandler);

    register(Date.class, new DateTypeHandler());
    register(Date.class, JdbcType.DATE, new DateOnlyTypeHandler());
    register(Date.class, JdbcType.TIME, new TimeOnlyTypeHandler());
    register(JdbcType.TIMESTAMP, new DateTypeHandler());
    register(JdbcType.DATE, new DateOnlyTypeHandler());
    register(JdbcType.TIME, new TimeOnlyTypeHandler());

    register(java.sql.Date.class, new SqlDateTypeHandler());
    register(java.sql.Time.class, new SqlTimeTypeHandler());
    register(java.sql.Timestamp.class, new SqlTimestampTypeHandler());

    register(String.class, JdbcType.SQLXML, new SqlxmlTypeHandler());

    register(Instant.class, new InstantTypeHandler());
    register(LocalDateTime.class, new LocalDateTimeTypeHandler());
    register(LocalDate.class, new LocalDateTypeHandler());
    register(LocalTime.class, new LocalTimeTypeHandler());
    register(OffsetDateTime.class, new OffsetDateTimeTypeHandler());
    register(OffsetTime.class, new OffsetTimeTypeHandler());
    register(ZonedDateTime.class, new ZonedDateTimeTypeHandler());
    register(Month.class, new MonthTypeHandler());
    register(Year.class, new YearTypeHandler());
    register(YearMonth.class, new YearMonthTypeHandler());
    register(JapaneseDate.class, new JapaneseDateTypeHandler());

    // issue #273
    register(Character.class, new CharacterTypeHandler());
    register(char.class, new CharacterTypeHandler());
  }

  /**
   * Set a default {@link TypeHandler} class for {@link Enum}.
   * A default {@link TypeHandler} is {@link org.apache.ibatis.type.EnumTypeHandler}.
   *
   * @param typeHandler a type handler class for {@link Enum}
   * @since 3.4.5
   */
  public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
    this.defaultEnumTypeHandler = typeHandler;
  }

  /**
   * 根据java类型和jdbc类型，获取TypeHandler：
   * (1)先根据java类型，从"java类型处理器集合(typeHandlerMap)"中，获取到对应的"jdbc类型处理器集合"；
   * (2)如果获取到了，则根据jdbc类型，从"jdbc类型处理器集合"中，获取到对应的TypeHandler；
   * (3)如果没有获取到，则返回null
   */
  public boolean hasTypeHandler(Class<?> javaType) {
    return hasTypeHandler(javaType, null);
  }

  public boolean hasTypeHandler(TypeReference<?> javaTypeReference) {
    return hasTypeHandler(javaTypeReference, null);
  }

  public boolean hasTypeHandler(Class<?> javaType, JdbcType jdbcType) {
    /**
     * 1、getTypeHandler((Type) javaType, jdbcType)：
     * >>> 根据java类型和jdbc类型，获取TypeHandler：
     * >>> 1、先根据java类型，从"java类型处理器集合(typeHandlerMap)"中，获取到对应的"jdbc类型处理器集合"；
     * >>> 2、如果能获取到"jdbc类型处理器集合"；则根据jdbc类型，从"jdbc类型处理器集合"中，获取到对应的TypeHandler；
     * >>> 3、如果没有获取到"java类型"对应的"jdbc类型处理器集合"，则返回null
     */
    // 存在javaType && 存在"根据java类型和jdbc类型，对应的TypeHandler"
    return javaType != null && getTypeHandler((Type) javaType, jdbcType) != null;
  }

  public boolean hasTypeHandler(TypeReference<?> javaTypeReference, JdbcType jdbcType) {
    return javaTypeReference != null && getTypeHandler(javaTypeReference, jdbcType) != null;
  }

  public TypeHandler<?> getMappingTypeHandler(Class<? extends TypeHandler<?>> handlerType) {
    return allTypeHandlersMap.get(handlerType);
  }

  public <T> TypeHandler<T> getTypeHandler(Class<T> type) {
    return getTypeHandler((Type) type, null);
  }

  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference) {
    return getTypeHandler(javaTypeReference, null);
  }

  public TypeHandler<?> getTypeHandler(JdbcType jdbcType) {
    return jdbcTypeHandlerMap.get(jdbcType);
  }

  /**
   * 根据java类型和jdbc类型，获取TypeHandler：
   * 1、先根据java类型，从"java类型处理器集合(typeHandlerMap)"中，获取到对应的"jdbc类型处理器集合"；
   * 2、如果能获取到"jdbc类型处理器集合"；则根据jdbc类型，从"jdbc类型处理器集合"中，获取到对应的TypeHandler；
   * 3、如果没有获取到"java类型"对应的"jdbc类型处理器集合"，则返回null
   *
   * 简单概括：从"java类型集合"中，获取java类型所对应的"jdbc类型集合"；然后从"jdbc类型集合"中，获取jdbc类型所对应的TypeHandler
   *
   * @param type     java类型
   * @param jdbcType jdbc类型
   */
  public <T> TypeHandler<T> getTypeHandler(Class<T> type, JdbcType jdbcType) {
    return getTypeHandler((Type) type, jdbcType);
  }

  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference, JdbcType jdbcType) {
    return getTypeHandler(javaTypeReference.getRawType(), jdbcType);
  }

  /**
   * 根据java类型和jdbc类型，获取TypeHandler：
   * 1、先根据java类型，从"java类型处理器集合(typeHandlerMap)"中，获取到对应的"jdbc类型处理器集合"；
   * 2、如果能获取到"jdbc类型处理器集合"；则根据jdbc类型，从"jdbc类型处理器集合"中，获取到对应的TypeHandler；
   * 3、如果没有获取到"java类型"对应的"jdbc类型处理器集合"，则返回null
   *
   * @param type     java类型
   * @param jdbcType jdbc类型
   */
  @SuppressWarnings("unchecked")
  private <T> TypeHandler<T> getTypeHandler(Type type, JdbcType jdbcType) {
    if (ParamMap.class.equals(type)) {
      return null;
    }

    /* 1、先根据java类型，从"java类型处理器集合(typeHandlerMap)"中，获取到对应的"jdbc类型处理器集合" */
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = getJdbcHandlerMap(type);

    TypeHandler<?> handler = null;

    /* 2、如果能获取到"java类型"对应的"jdbc类型处理器集合"；则根据jdbc类型，从"jdbc类型处理器集合"中，获取到对应的TypeHandler */
    // 获取到了对应的"jdbc类型处理器集合"
    // 题外：TypeHandler，称之为"jdbc类型处理器"，不知道是否可以这么称呼！
    if (jdbcHandlerMap != null) {

      /* 2.1、根据jdbc类型，从"jdbc类型处理器集合"中，获取到对应的TypeHandler */
      // 题外：jdbc类型处理器的作用：将jdbc类型转换为java类型进行返回！
      handler = jdbcHandlerMap.get(jdbcType);

      /* 2.2、如果从"jdbc类型处理器集合"中，获取对应的"jdbc类型处理器"为空，则从"jdbc类型处理器集合"中，获取默认的TypeHandler */
      if (handler == null) {
        handler = jdbcHandlerMap.get(null);
      }

      /*

      2.3、从"jdbc类型处理器集合"中，没有获取到默认的"jdbc类型处理器"，则尝试获取唯一的TypeHandler：
      （1）如果只存在一种类型的TypeHandler，则返回这个唯一的TypeHandler；
      （2）如果存在多种类型的TypeHandler，则返回null

       */
      if (handler == null) {
        // #591

        // 选择唯一的TypeHandler：
        // 1、如果只存在一种类型的TypeHandler，则返回这个唯一的TypeHandler；
        // 2、如果存在多种类型的TypeHandler，则返回null
        handler = pickSoleHandler/* 选择唯一的处理程序 */(jdbcHandlerMap);
      }
    }

    /* 3、如果没有获取到"java类型"对应的"jdbc类型处理器集合"，则返回null */

    /* 4、返回jdbc类型处理器 */
    // type drives generics here —— 类型驱动泛型在这里
    return (TypeHandler<T>) handler;
  }

  /**
   * 根据java类型，从"java类型处理器集合(typeHandlerMap)"中，获取到对应的"jdbc类型处理器集合"
   *
   * @param type java类型
   * @return
   */
  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMap(Type type) {
    // 根据java类型，从"java类型处理器集合(typeHandlerMap)"中，获取到对应的"jdbc类型处理器集合"
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(type);
    if (jdbcHandlerMap != null) {
      // 如果获取到了，并且不是空集合，就直接返回获取到的；否则返回null
      return NULL_TYPE_HANDLER_MAP.equals(jdbcHandlerMap) ? null : jdbcHandlerMap;
    }

    if (type instanceof Class) {
      Class<?> clazz = (Class<?>) type;
      if (Enum.class.isAssignableFrom(clazz)) {
        Class<?> enumClass = clazz.isAnonymousClass() ? clazz.getSuperclass() : clazz;
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(enumClass, enumClass);
        if (jdbcHandlerMap == null) {
          register(enumClass, getInstance(enumClass, defaultEnumTypeHandler));
          return typeHandlerMap.get(enumClass);
        }
      } else {
        jdbcHandlerMap = getJdbcHandlerMapForSuperclass(clazz);
      }
    }
    typeHandlerMap.put(type, jdbcHandlerMap == null ? NULL_TYPE_HANDLER_MAP : jdbcHandlerMap);
    return jdbcHandlerMap;
  }

  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForEnumInterfaces(Class<?> clazz, Class<?> enumClazz) {
    for (Class<?> iface : clazz.getInterfaces()) {
      Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(iface);
      if (jdbcHandlerMap == null) {
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(iface, enumClazz);
      }
      if (jdbcHandlerMap != null) {
        // Found a type handler registered to a super interface
        HashMap<JdbcType, TypeHandler<?>> newMap = new HashMap<>();
        for (Entry<JdbcType, TypeHandler<?>> entry : jdbcHandlerMap.entrySet()) {
          // Create a type handler instance with enum type as a constructor arg
          newMap.put(entry.getKey(), getInstance(enumClazz, entry.getValue().getClass()));
        }
        return newMap;
      }
    }
    return null;
  }

  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForSuperclass(Class<?> clazz) {
    Class<?> superclass = clazz.getSuperclass();
    if (superclass == null || Object.class.equals(superclass)) {
      return null;
    }
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(superclass);
    if (jdbcHandlerMap != null) {
      return jdbcHandlerMap;
    } else {
      return getJdbcHandlerMapForSuperclass(superclass);
    }
  }

  /**
   * 选择唯一的TypeHandler：
   * 1、如果只存在一种类型的TypeHandler，则返回这个唯一的TypeHandler；
   * 2、如果存在多种类型的TypeHandler，则返回null
   *
   * @param jdbcHandlerMap
   * @return
   */
  private TypeHandler<?> pickSoleHandler/* 选择唯一的处理程序 */(Map<JdbcType, TypeHandler<?>> jdbcHandlerMap) {
    TypeHandler<?> soleHandler = null;

    for (TypeHandler<?> handler : jdbcHandlerMap.values()) {
      // 先设置为第一个TypeHandler
      if (soleHandler == null) {
        soleHandler = handler;
      }
      // 进入下次循环，如果还存在其它的TypeHandler，则直接返回null
      // ⚠️也就是说这里有2种情况：1、如果只存在一种类型的TypeHandler，则返回这个唯一的TypeHandler；2、如果存在多种类型的TypeHandler，则返回null
      else if (!handler.getClass().equals(soleHandler.getClass())) {
        // More than one type handlers registered. —— 注册了多个类型的处理程序。

        return null;
      }
    }
    return soleHandler;
  }

  public TypeHandler<Object> getUnknownTypeHandler() {
    return unknownTypeHandler;
  }

  public void register(JdbcType jdbcType, TypeHandler<?> handler) {
    jdbcTypeHandlerMap.put(jdbcType, handler);
  }

  //
  // REGISTER INSTANCE
  //

  // Only handler

  /**
   * 往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"
   * ⚠️注意：里面会获取类上的@MappedJdbcTypes，得到jdbc类型
   */
  @SuppressWarnings("unchecked")
  public <T> void register(TypeHandler<T> typeHandler) {

    // 是否找到了java类型的标识（存在@MappedTypes，且@MappedTypes中标注了java类型）
    boolean mappedTypeFound = false;

    /**
     * MappedJdbcTypes的注解的用法可参考测试类StringTrimmingTypeHandler
     * 另外在文档中也提到，这是扩展自定义的typeHandler所需要的
     * (你可以重写类型处理器或创建你自己的类型处理器来处理不支持的或非标准的类型)
     */
    // 1、获取类上的@MappedTypes
    MappedTypes mappedTypes = typeHandler.getClass().getAnnotation(MappedTypes.class);
    if (mappedTypes != null) {
      // 遍历java类型
      for (Class<?> handledType : mappedTypes.value()) {

        // 往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"
        // ⚠️注意：里面会获取类上的@MappedJdbcTypes，得到jdbc类型
        register(handledType, typeHandler);

        // 找到了java类型，所以将"是否找到了java类型的标识"设置为true
        mappedTypeFound = true;
      }
    }

    // @since 3.1.0 - try to auto-discover the mapped type
    // 2、不存在java类型，但是TypeHandler是TypeReference类型
    if (!mappedTypeFound && typeHandler instanceof TypeReference) {
      try {
        TypeReference<T> typeReference = (TypeReference<T>) typeHandler;
        // 往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"
        // ⚠️注意：里面会获取类上的@MappedJdbcTypes，得到jdbc类型
        register(typeReference.getRawType()/* java类型 */, typeHandler);
        // 找到了java类型，所以将"是否找到了java类型的标识"设置为true
        mappedTypeFound = true;
      } catch (Throwable t) {
        // maybe users define the TypeReference with a different type and are not assignable, so just ignore it
        // 上面的翻译：也许用户用不同的类型定义TypeReference并且不可分配，所以忽略它
      }
    }

    // 3、未找到java类型
    if (!mappedTypeFound) {
      // null java类型的注册：往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"，只不过这里的java类型为null
      // ⚠️注意：里面会获取类上的@MappedJdbcTypes，得到jdbc类型
      register((Class<T>) null/* java类型 */, typeHandler);
    }
  }

  // java type + handler

  /**
   * 往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"
   *
   * @param javaType    java类型
   * @param typeHandler TypeHandler对象
   */
  public <T> void register(Class<T> javaType, TypeHandler<? extends T> typeHandler) {
    register((Type) javaType, typeHandler);
  }

  /**
   * 往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"
   *
   * @param javaType    java类型
   * @param typeHandler TypeHandler对象
   */
  private <T> void register(Type javaType, TypeHandler<? extends T> typeHandler) {
    /* 1、获取类上的@MappedJdbcTypes */
    MappedJdbcTypes mappedJdbcTypes = typeHandler.getClass().getAnnotation(MappedJdbcTypes.class);

    /* 2.1、类上存在@MappedJdbcTypes */
    if (mappedJdbcTypes != null) {
      /* 1.1、遍历@MappedJdbcTypes中配置的jdbc类型，往"java类型处理器集合"当中，注册"java类型"和对应的"jdbc类型处理器集合" */
      // 有jdbc类型
      for (JdbcType handledJdbcType : mappedJdbcTypes.value()) {
        register(javaType, handledJdbcType, typeHandler);
      }

      /* 1.2、是空Jdbc类型，往"java类型处理器集合"当中，注册"java类型"和对应的"jdbc类型处理器集合"，只不过Jdbc类型为null */
      // jdbc类型为null
      if (mappedJdbcTypes.includeNullJdbcType()/* 包括空Jdbc类型 */) {
        register(javaType, null, typeHandler);
      }
    }
    /* 2.2、类上不存在@MappedJdbcTypes */
    else {
      /* 2.1、往"java类型处理器集合"当中，注册"java类型"和对应的"jdbc类型处理器集合"，只不过Jdbc类型为null */
      // jdbc类型为null
      register(javaType, null, typeHandler);
    }

  }

  public <T> void register(TypeReference<T> javaTypeReference, TypeHandler<? extends T> handler) {
    register(javaTypeReference.getRawType(), handler);
  }

  // java type + jdbc type + handler

  // Cast is required here

  /**
   * 往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"
   *
   * @param type     java类型
   * @param jdbcType jdbc类型
   * @param handler  TypeHandler对象
   */
  @SuppressWarnings("cast")
  public <T> void register(Class<T> type, JdbcType jdbcType, TypeHandler<? extends T> handler) {
    register((Type) type, jdbcType, handler);
  }

  /**
   * 往"java类型处理器集合"当中，注册"java类型"和对应的"jdbc类型处理器集合"
   *
   * @param javaType java类型
   * @param jdbcType jdbc类型
   * @param handler  TypeHandler对象
   */
  private void register(Type javaType, JdbcType jdbcType, TypeHandler<?> handler) {
    /* 1、存在javaType，才能往"java类型处理器集合"中注册 */
    if (javaType != null) {

      /* 1.1、去"java类型处理器集合"当中，获取"java类型"对应的"jdbc类型处理器集合" */
      Map<JdbcType, TypeHandler<?>> map = typeHandlerMap.get(javaType);

      // 如果"jdbc类型处理器集合"为null，则创建空数据的"jdbc类型处理器集合"对象
      if (map == null || map == NULL_TYPE_HANDLER_MAP) {
        map = new HashMap<>();
      }

      /* 1.2、将"jdbc类型"与"对应的类型处理器"，放入"jdbc类型处理器集合"中 */
      map.put(jdbcType, handler);

      /* 1.3、将"java类型"与"对应的jdbc类型处理器集合"，放入"java类型处理器集合"中 */
      typeHandlerMap.put(javaType, map);
    }

    /* 2、往"所有类型处理器集合"当中，放入类型处理器Class和类型处理器 */
    allTypeHandlersMap.put(handler.getClass(), handler);
  }

  //
  // REGISTER CLASS
  //

  // Only handler type

  public void register(Class<?> typeHandlerClass) {

    // 是否找到了java类型的标识（存在@MappedTypes，且@MappedTypes中标注了java类型）
    boolean mappedTypeFound/* 找到映射类型 */ = false;

    /* 1、获取类上的@MappedTypes */
    MappedTypes mappedTypes = typeHandlerClass.getAnnotation(MappedTypes.class);

    if (mappedTypes != null) {
      // 遍历@MappedTypes上的java类型
      for (Class<?> javaTypeClass : mappedTypes.value()) {
        // 往"java类型处理器集合"当中，注册"java类型"和对应的"jdbc类型处理器集合"
        // 注意：⚠️里面会获取类上的@MappedJdbcTypes，得到jdbc类型
        register(javaTypeClass, typeHandlerClass);

        // 找到了java类型，所以将"是否找到了java类型的标识"设置为true
        mappedTypeFound = true;
      }
    }

    // 没有找到java类型
    if (!mappedTypeFound) {
      register(getInstance(null, typeHandlerClass)/* 实例化TypeHandler */);
    }

  }

  // java type + handler type

  public void register(String javaTypeClassName, String typeHandlerClassName) throws ClassNotFoundException {
    register(Resources.classForName(javaTypeClassName), Resources.classForName(typeHandlerClassName));
  }

  /**
   * 往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"
   *
   * @param javaTypeClass    java类型
   * @param typeHandlerClass TypeHandler Class
   */
  public void register(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    register(javaTypeClass, getInstance(javaTypeClass, typeHandlerClass)/* 实例化TypeHandler */);
  }

  // java type + jdbc type + handler type

  public void register(Class<?> javaTypeClass, JdbcType jdbcType, Class<?> typeHandlerClass) {
    register(javaTypeClass, jdbcType, getInstance(javaTypeClass, typeHandlerClass));
  }

  // Construct a handler (used also from Builders) —— 构造一个handler(也从Builders中使用)


  /**
   * 实例化TypeHandler
   * (1)如果存在java类型，则java类型作为构造参数，去实例化TypeHandler；
   * (2)如果不存在java类型，则使用默认构造器，去实例化TypeHandler
   *
   * @param javaTypeClass    java类型
   * @param typeHandlerClass TypeHandler class
   */
  @SuppressWarnings("unchecked")
  public <T> TypeHandler<T> getInstance(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    /* 1、存在java类型，java类型作为入参，去实例化TypeHandler */
    if (javaTypeClass != null) {
      try {
        // 获取构造函数
        Constructor<?> c = typeHandlerClass.getConstructor(Class.class);
        // 实例化TypeHandler，java类型作为构造参数
        return (TypeHandler<T>) c.newInstance(javaTypeClass);
      } catch (NoSuchMethodException ignored) {
        // ignored
      } catch (Exception e) {
        throw new TypeException("Failed invoking constructor for handler " + typeHandlerClass, e);
      }
    }

    /* 2、不在java类型，使用默认构造器，实例化TypeHandler */
    try {
      Constructor<?> c = typeHandlerClass.getConstructor();
      return (TypeHandler<T>) c.newInstance();
    } catch (Exception e) {
      throw new TypeException("Unable to find a usable constructor for " + typeHandlerClass, e);
    }
  }

  // scan

  /**
   * 扫描指定包下的TypeHandler，往java类型处理器集合当中注册
   */
  public void register(String packageName) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();

    // 查找指定包下实现了TypeHandler接口的类（查询指定包下是TypeHandler类型的类）
    resolverUtil.find(new ResolverUtil.IsA(TypeHandler.class), packageName);

    // 获取查询到的所有实现了TypeHandler接口的类
    Set<Class<? extends Class<?>>> handlerSet = resolverUtil.getClasses();

    for (Class<?> type : handlerSet) {
      //Ignore inner classes and interfaces (including package-info.java) and abstract classes
      if (!type.isAnonymousClass()/* 是匿名类 */ && !type.isInterface()/* 是接口 */ && !Modifier.isAbstract(type.getModifiers())/* 是抽象的 */) {
        /* 不是匿名类 && 不是接口 && 不是抽象类 */
        // 往java类型处理器集合当中，注册"java类型"和对应的"jdbc类型处理器集合"
        // 注意：⚠️里面会获取类上的@MappedTypes，得到java类型
        // 注意：⚠️里面会获取类上的@MappedJdbcTypes，得到jdbc类型
        register(type);
      }
    }
  }

  // get information

  /**
   * Gets the type handlers.
   *
   * @return the type handlers
   * @since 3.2.2
   */
  public Collection<TypeHandler<?>> getTypeHandlers() {
    return Collections.unmodifiableCollection(allTypeHandlersMap.values());
  }

}
