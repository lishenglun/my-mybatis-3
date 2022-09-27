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

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;
import org.apache.ibatis.util.MapUtil;

/**
 * @author Clinton Begin
 */
public class TransactionalCacheManager {

  /**
   * 1、之所以是个集合，是因为，可以通过SqlSession获取多个不同接口的代理对象。每个接口对应着一个mapper，对应着一个二级缓存。
   * 不同的接口就对应着不同的二级缓存。所以是一个集合存放多个二级缓存！
   * <p>
   * 2、题外：只要是同一个sqlSession获取的接口代理对象，无论是相同的接口代理对象，还是不同的接口代理对象，最终，代理对象在执行的时候，底层调用的都是同一个SqlSession，
   * 一个SqlSession里面，有这个SqlSession里面专属的CachingExecutor，所以通过同一个SqlSession获取的形形色色的接口对象，底层走的都是同一个CachingExecutor，
   * 以及其它的东西，底层走的都是同一个SqlSession的同一套东西
   */
  // 事务缓存集合
  // key：二级缓存
  // value：二级缓存对应的TransactionalCache(事务缓存)
  private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

  /**
   * 标识commit时需要"清除当前二级缓存"
   *
   * 注意：⚠️这里只是标识一下在commit时需要清除当前二级缓存，并未真正清除！另外，如果当前二级缓存被标识为在commit时需要被清除，则也代表当前二级缓存中的数据是无效的、"当前二级缓存的数据"是作为已清除的数据、当前二级缓存是没有数据的，即使有数据，也代表没有数据，是空的
   *
   * @param cache 二级缓存
   */
  public void clear(Cache cache) {
    // 标识需要"清除二级缓存"（注意：⚠️只是做一个标识！）
    getTransactionalCache(cache).clear();
  }

  public Object getObject(Cache cache, CacheKey key) {
    /**
     * 1、getTransactionalCache(cache)：
     * 从transactionalCaches中，获取Cache对应的TransactionalCache；
     * 如果不存在，就创建Cache对应的TransactionalCache放入transactionalCaches中，并返回这个TransactionalCache
     */
    // TransactionalCache.getObject(key)
    return getTransactionalCache(cache).getObject(key);
  }

  /**
   * 将查询到的数据暂存到TransactionalCache.entriesToAddOnCommit，
   * 后续在sqlSession.close()的时候，会调用TransactionalCache#flushPendingEntries()，将entriesToAddOnCommit中的数据添加到二级缓存中，
   * 所以可以直接理解为就是往二级缓存中添加查询到的数据！
   *
   * @param cache 二级缓存对象
   * @param key   CacheKey
   * @param value 查询到的数据对象
   */
  public void putObject(Cache cache, CacheKey key, Object value) {
    getTransactionalCache(cache).putObject(key, value);
  }

  /**
   * 遍历当前SqlSession中操作过的的二级缓存，对每个二级缓存进行：
   * (1)如果当前二级缓存被标识为commit时需要清空，则清空当前二级缓存中的数据
   * (2)接着，将"commit时需要往二级缓存中添加的数据"，缓存到二级缓存
   * 题外：也就是说，上面清空完毕二级缓存，下面又接着添加数据，如果有的话
   * (3)重置当前二级缓存对应的TransactionalCache为刚创建时的样子
   */
  public void commit() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      // commit时对二级缓存的操作
      // (1)如果当前二级缓存被标识为"commit时需要清空"，则清空当前二级缓存中的数据
      // (2)接着，将"commit时需要往二级缓存中添加的数据"，缓存到二级缓存
      // 题外：也就是说，上面清空完毕二级缓存，下面又接着添加数据，如果有的话
      // (3)重置当前二级缓存对应的TransactionalCache为刚创建时的样子
      txCache.commit();
    }
  }

  /**
   * 遍历当前SqlSession中操作过的的二级缓存，对每个二级缓存进行：
   * (1)移除二级缓存中"缺失的条目"，也就是：二级缓存中没有哪个CacheKey对应的数据，就移除这个CacheKey
   * (2)重置当前二级缓存的TransactionalCache为刚创建时的样子
   */
  public void rollback() {
    // 遍历当前SqlSession中操作过的的二级缓存
    for (TransactionalCache txCache : transactionalCaches.values()) {
      // (1)移除二级缓存中"缺失的条目"，也就是：二级缓存中没有哪个CacheKey对应的数据，就移除这个CacheKey
      // (2)重置当前二级缓存的TransactionalCache为刚创建时的样子
      txCache.rollback();
    }
  }

  /**
   * 从transactionalCaches中，获取Cache对应的TransactionalCache；
   * 如果不存在，就创建Cache对应的TransactionalCache放入transactionalCaches中，并返回这个TransactionalCache（Cache会作为TransactionalCache构造器参数）
   */
  private TransactionalCache getTransactionalCache(Cache cache) {
    return MapUtil.computeIfAbsent(transactionalCaches, cache, TransactionalCache::new);
  }

}
