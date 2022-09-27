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
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 参数名称解析器
 */
public class ParamNameResolver {

  public static final String GENERIC_NAME_PREFIX = "param";

  // 是否使用实际参数名称
  private final boolean useActualParamName;

  /**
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  // 记录"参数索引位置"和"参数名称"之间的对应关系
  // 1、先获取参数上的@Param，如果有，就从@Param中获取参数名称
  // 2、如果参数上没有@Param：
  // >>>（1）则看下是否允许使用实际参数名称(默认为true)，如果允许，则使用实际参数名称。一般为arg0，arg1，arg2之类的
  // >>>（2）如果不允许使用实际参数名称，则使用参数的索引作为参数名称
  private final SortedMap<Integer, String> names;

  // 参数上是否存在@Param
  private boolean hasParamAnnotation;

  /**
   * 解析得到"参数索引位置"和"参数名称"之间的对应关系
   * 1、先获取参数上的@Param，如果有，就从@Param中获取参数名称
   * 2、如果参数上没有@Param：
   * （1）则看下是否允许使用实际参数名称(默认为true)，如果允许，则使用实际参数名称。一般为arg0，arg1，arg2之类的
   * （2）如果不允许使用实际参数名称，则使用参数的索引作为参数名称
   *
   * @param config
   * @param method
   */
  public ParamNameResolver(Configuration config, Method method) {
    // 是否使用实际参数名称，默认为true
    this.useActualParamName = config.isUseActualParamName();

    // 获取方法参数类型列表
    final Class<?>[] paramTypes = method.getParameterTypes();
    // 获取方法参数上的所有注解
    // 题外：一个参数上可以定义多个注解，所以一个参数对应一个注解数组Annotation[]；然后一个方法可以定义多个参数，则是Annotation[][]
    // 注意：⚠️即使参数上没定义一个注解，那么也会有对应的Annotation[]对象，只不过里面不存在数据，所以方法有几个参数，那么paramAnnotations.length就是几
    // >>> 例如：getUserList(User user,Integer id,Account account)，虽然参数上没定义一个注解，但是paramAnnotations.length = 3
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();

    // 该集合用于记录"参数索引位置"与"参数名称"之间的对应关系
    final SortedMap<Integer, String> map = new TreeMap<>();

    // 参数个数
    int paramCount = paramAnnotations.length;

    /* 一、获取"参数索引位置"和"参数名称"之间的对应关系 */

    /* 遍历方法参数 */

    // get names from @Param annotations —— 从@Param中获取名称
    for (int paramIndex/* 参数索引位置 */ = 0; paramIndex < paramCount; paramIndex++) {

      /* 1、如果参数是RowBounds或ResultHandler类型，则跳过 */
      // 判断是不是特殊参数，是的话就跳过。RowBounds和ResultHandler就是特殊参数！也就是说要跳过RowBounds和ResultHandler
      // 如果参数是RowBounds类型或ResultHandler类型，则跳过对该参数的分析
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters —— 跳过特殊参数
        continue;
      }

      /* 2、先获取参数上的@Param，如果有，就从@Param中获取参数名称 */
      String name = null;
      // 遍历参数上的注解
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        // 如果参数上存在@Param
        if (annotation instanceof Param) {
          // @Param注解出现过一次，就将"参数上是否存在@Param"标识设置为true
          hasParamAnnotation = true;
          // 获取@Param指定的参数名称
          // 题外：我们的方法参数名称在编译之后，会变成args0之类的名称，但是带了@Param这样的注解之后，就可以按照正常的参数名字来帮你进行保存！
          name = ((Param) annotation).value();
          break;
        }
      }

      /* 3、如果参数上没有@Param */
      if (name == null) {
        // @Param was not specified. —— 未指定@Param

        // 参数没有对应的@Param注解

        /* 2.1、看下是否允许使用实际参数名称，如果允许使用实际参数名称(默认为true)，则使用实际参数名称。一般为arg0，arg1，arg2之类的 */

        // 判断"是否使用实际参数名称（默认为true）"，如果允许，则使用实际参数名称。一般为arg0，arg1，arg2之类的
        // 例如：getUserList(User user,Integer id,Account account)，在编译后，参数名称就是getUserList(User arg0,Integer arg1,Account arg2)
        //if (useActualParamName/* 是否使用实际参数名称 */) {
        //  name = getActualParamName(method, paramIndex);  // arg0
        //}

        /* 2.2、如果不允许使用实际参数名称，则使用参数的索引作为参数名称 */
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...) —— 使用参数索引作为名称（“0”，“1”，...）
          // gcode issue #71 —— gcode问题71

          // 使用参数的索引作为参数名称
          // 题外：最开始map.size是0，放入之后为1，所以当前的map.size是对应着参数索引位置的！
          name = String.valueOf(map.size());
        }
      }

      /* 记录"参数索引位置"和"参数名称"之间的对应关系 */
      map.put(paramIndex, name);
    }

    // 弄成一个不可更改的集合，永久的记录"参数索引位置"和"参数名称"之间的对应关系
    names = Collections.unmodifiableSortedMap(map);
  }

  // 过滤RowBounds和ResultHandler两种类型的参数
  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  /**
   * 判断是不是特殊参数，是的话就跳过。RowBounds和ResultHandler就是特殊参数！也就是说要跳过RowBounds和ResultHandler
   */
  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   *
   * @return the names
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * 获取"参数名"与"实参"之间的对应关系：
   * 1、方法没有参数，或者不存在有效参数，则返回null
   * 2、方法只有一个有效参数，且未使用@Param，则直接获取入参对象返回（没有参数名）
   * 3、方法有效参数使用了@Param，或者存在多个有效参数，则返回【"参数名称"与"实参(入参对象)"之间的对应关系】
   *
   * <p>
   * A single non-special parameter is returned without a name.
   * Multiple parameters are named using the naming rule.
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   *
   * @param args the args
   * @return the named params
   */
  public Object getNamedParams(Object[] args) {
    // 获取方法参数的个数
    final int paramCount = names.size();
    /* 1、方法没有参数，或者不存在有效参数，则返回null */
    // 注意：只有当方法没有参数时，args是null；如果方法有参数，虽然调用方法传入的参数值是null，但是args不会是null，而是一个对象，只不过是一个空对象，里面没有数据
    // 题外：paramCount == 0，也代表没有方法没有参数。
    // >>> 之所以也要这么判断一下，是因为，如果方法上存在RowBounds或ResultHandler类型参数，那么args是不会为null的；
    // >>> 但是RowBounds或ResultHandler类型参数是不作为sql语句参数值的，是无效的，也只有names集合中会忽略掉这2个参数类型
    // >>> 所以需要这么判断一下，确保，如果方法没有可处理的sql语句参数，就返回null
    if (args == null || paramCount == 0) {
      return null;
    }
    /* 2、方法只有一个有效参数，且未使用@Param，则直接获取入参对象返回（没有参数名） */
    // 方法中没有参数使用@Param && 只有一个参数
    else if (!hasParamAnnotation && paramCount == 1) {
      /**
       * 1、names.firstKey()：获取names集合中的第一个数据的key，也就是参数索引位置。
       * 疑问：为什么不直接写0，而是names.firstKey()获取参数索引位置？
       * >>> 因为参数上可能存在RowBounds或ResultHandler类型参数，外加一个其它参数，
       * >>> 例如：User getAllUserRowBounds(RowBounds rowBounds,Integer id);
       * >>> 虽然是2个参数，但是可以作为sql语句的有效参数，就是"Integer id"这一个，但是该参数的索引位置是1，不是0，所以不能直接写0
       */
      // 通过参数索引位置，获取参数值
      Object value = args[names.firstKey()/* 参数索引位置 */];

      // 如果是Collection或者数组就包装参数值，然后返回参数值
      return wrapToMapIfCollection(value, useActualParamName ? names.get(0) : null);
    }
    /* 3、方法有效参数使用了@Param，或者存在多个有效参数，则返回【"参数名称"与"实参(入参对象)"之间的对应关系】 */
    else {
      // ⚠️记录"参数名称"与"实参"之间的对应关系
      // 题外：ParamMap继承了HashMap.如果向ParamMap中添加己经存在的key，会覆盖
      final Map<String, Object> param = new ParamMap<>();

      int i = 0;
      // names：记录"参数索引位置"和"参数名称"之间的对应关系
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        /* 3.1、️记录"参数名称"与"实参"之间的对应关系 */
        /**
         * 题外：参数未使用@Param，names保存的参数名称，有可能是arg0、arg1这种。
         */
        param.put(entry.getValue()/* 参数名称 */, args[entry.getKey()]/* 通过参数索引，获取实参 */);

        /* 3.2、记录"参数索引位置"和"通用参数名称"之间的对应关系 */
        /**
         * 1、注意：⚠️这里通用参数名称的索引位置和参数实际索引可能对应不上，它只是一个有效参数的排序，
         * 例如：User getAllUserRowBounds(RowBounds rowBounds,ResultHandler rh,Integer id);
         * id参数索引为2。按理说id的通用参数名称，应该是"param2"，但是这里实际却为"param1"，因为id这个参数只是作为第1个有效参数，
         * 所以，id的通过参数名称为"param1"
         */
        // add generic param names (param1, param2, ...) —— 添加通用参数名称（param1，param2，...）
        // 为参数创建通用参数名称 = param+索引（param1，param2，...），并添加到param集合中
        final String genericParamName/* 通用参数名称 */ = GENERIC_NAME_PREFIX/* param */ + (i + 1);
        // ensure not to overwrite parameter named with @Param —— 确保不覆盖以@Param命名的参数
        // 如果names集合中不存在genericParamName名称的参数，则添加通用参数名称到param集合中 —— 确保不覆盖以@Param命名的参数
        if (!names.containsValue(genericParamName)) {
          /**
           * 1、我们可以写多个方法参数，默认情况下，它们将会以"param+它们在参数列表中的位置"来命名，比如：#{param1},#{param2}等
           * 如果你想改变参数的名称，可以在参数上使用@Param
           */
          // ⚠️记录"参数索引位置"和"通用参数名称"之间的对应关系
          param.put(genericParamName/* 通用参数名称 */, args[entry.getKey()/* 通过参数索引，获取实参 */]);
        }
        i++;
      }
      return param;
    }
  }

  /**
   * 如果sql参数值对象是Collection或者是数组，则用ParamMap包装一下；否则，什么都不做，则原生返回"sql参数值对象"；
   *
   *
   * Wrap to a {@link ParamMap} if object is {@link Collection} or array. —— 如果对象是 {@link Collection} 或数组，则包装到 {@link ParamMap}。
   *
   * @param object          a parameter object
   *                        sql参数值对象
   * @param actualParamName an actual parameter name
   *                        (If specify a name, set an object to {@link ParamMap} with specified name)
   * @return a {@link ParamMap}
   * @since 3.5.5
   */
  public static Object wrapToMapIfCollection(Object object, String actualParamName) {
    /* 1、集合 */
    if (object instanceof Collection) {
      ParamMap<Object> map = new ParamMap<>();
      // 包装一下
      map.put("collection", object);

      // list
      if (object instanceof List) {
        // 包装一下
        map.put("list", object);
      }

      // 如果actualParamName不为null，则把actualParamName做为key，object作为value，也添加到map中
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }

    /* 2、数组 */
    else if (object != null && object.getClass().isArray()) {
      ParamMap<Object> map = new ParamMap<>();
      // 包装一下
      map.put("array", object);
      // 如果actualParamName不为null，则把actualParamName做为key，object作为value，也添加到map中
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));

      return map;
    }

    /* 2、既不是集合，也不是数组，则原生返回 */
    return object;
  }

}
