package com.msb.other.objectWrapperFactory;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.wrapper.MapWrapper;

import java.util.Map;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/15 5:41 下午
 */
public class MyMapWrapper extends MapWrapper {

  public MyMapWrapper(MetaObject metaObject, Map<String, Object> map) {
    super(metaObject, map);
  }

  @Override
  public String findProperty(String name, boolean useCamelCaseMapping) {
    // 此处需要在 settings 里面配置 mapUnderscoreToCamelCase 为 true
    if (useCamelCaseMapping
      && ((name.charAt(0) >= 'A' && name.charAt(0) <= 'Z')
      || name.contains("_"))) {
      return underlineToCamelCase(name);
    }
    return name;
  }

  // 将下划线进行驼峰转换
  public String underlineToCamelCase(String inputString) {
    StringBuilder sb = new StringBuilder();
    boolean nextUpperCase = false;
    for (int i = 0; i < inputString.length(); i++) {
      char c = inputString.charAt(i);
      if (c == '_') {
        if (sb.length() > 0) {
          nextUpperCase = true;
        }
      } else {
        if (nextUpperCase) {
          sb.append(Character.toUpperCase(c));
          nextUpperCase = false;
        } else {
          sb.append(Character.toLowerCase(c));
        }
      }
    }
    return sb.toString();
  }

}

