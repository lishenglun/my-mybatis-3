package com.msb.other.objectWrapperFactory;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

import java.util.Map;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 *
 * 目的：对返回值 Map 做一层将带下划线的 key 值变成驼峰命名的封装
 *
 * @date 2022/8/15 5:42 下午
 */
public class MapWrapperFactory implements ObjectWrapperFactory {

  @Override
  public boolean hasWrapperFor(Object object) {
    return object instanceof Map;
  }

  @Override
  public ObjectWrapper getWrapperFor(MetaObject metaObject, Object object) {
    return new MyMapWrapper(metaObject, (Map<String, Object>)object);
  }

}
