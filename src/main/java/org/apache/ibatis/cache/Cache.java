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
package org.apache.ibatis.cache;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * 缓存接口：不管你是哪个缓存，在进行实现的时候，里面都需要实现Cache接口
 *
 * 题外：在缓存这块，mybatis用的是装饰者模式，看【decorators】包名称就知道了。
 *
 * 装饰者模式：在不改变原有对象的基础之上，将一些功能附加到对象里面，而且提供对应的一些扩展功能
 *
 * <p>
 * SPI for cache providers.
 * <p>
 * One instance of cache will be created for each namespace.
 * <p>
 * The cache implementation must have a constructor that receives the cache id as an String parameter.
 * <p>
 * MyBatis will pass the namespace as id to the constructor.
 *
 * <pre>
 * public MyCache(final String id) {
 *   if (id == null) {
 *     throw new IllegalArgumentException("Cache instances require an ID");
 *   }
 *   this.id = id;
 *   initialize();
 * }
 * </pre>
 *
 * @author Clinton Begin
 */

public interface  Cache {

  /**
   * 该缓存对象的id
   *
   * @return The identifier of this cache
   */
  String getId();

  /**
   * 向缓存中添加数据，Key是CacheKey，value 是查询结果
   *
   * @param key   Can be any object but usually it is a {@link CacheKey} —— 可以是任何对象，但通常是 {@link CacheKey}
   * @param value The result of a select.
   */
  void putObject(Object key, Object value);

  /**
   * 从缓存中获取数据
   *
   * @param key The key
   * @return The object stored in the cache.
   */
  Object getObject(Object key);

  /**
   * 从缓存中删除数据
   * <p>
   * As of 3.3.0 this method is only called during a rollback
   * for any previous value that was missing in the cache.
   * This lets any blocking cache to release the lock that
   * may have previously put on the key.
   * A blocking cache puts a lock when a value is null
   * and releases it when the value is back again.
   * This way other threads will wait for the value to be
   * available instead of hitting the database.
   *
   * @param key The key
   * @return Not used
   */
  Object removeObject(Object key);

  /**
   * 清空缓存
   * <p>
   * Clears this cache instance.
   */
  void clear();

  /**
   * 获取缓存项的个数
   * <p>
   * Optional. This method is not called by the core.
   *
   * @return The number of elements stored in the cache (not its capacity).
   */
  int getSize();

  /**
   * 获取读写锁
   * <p>
   * Optional. As of 3.2.6 this method is no longer called by the core.
   * <p>
   * Any locking needed by the cache must be provided internally by the cache provider.
   *
   * @return A ReadWriteLock
   */
  // 获取读写锁, 从3.2.6开始没用了，要SPI自己实现锁
  default ReadWriteLock getReadWriteLock() {
    return null;
  }

}
