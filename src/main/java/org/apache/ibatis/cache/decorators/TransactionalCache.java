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
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 每个二级缓存对应的TransactionalCache
 *
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  // ⚠️二级缓存
  private final Cache delegate;

  // commit时是否清除当前二级缓存的标识（true：清除；false：不清除）。
  // 题外：⚠️如果这个标识为true，也代表当前二级缓存中的数据是无效的、失效的、"当前二级缓存的数据"是作为已清除的数据、当前二级缓存是没有数据的，即使有数据，也代表没有数据，是空的
  private boolean clearOnCommit;

  // 暂存"sqlSession.commit时需要往二级缓存中添加的数据"集合
  // key：CacheKey
  // value：查询到的结果
  private final Map<Object, Object> entriesToAddOnCommit/* 提交时添加的条目 */;
  // 二级缓存中缺失的CacheKey数据（二级缓存中没有哪个CacheKey对应的数据，就存储哪个CacheKey）
  private final Set<Object> entriesMissedInCache/* 缓存中缺失的条目 */;

  /**
   * @param delegate 二级缓存对象
   */
  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 从二级缓存中获取数据
   *
   * @param key CacheKey
   */
  @Override
  public Object getObject(Object key) {
    /* 1、从二级缓存中获取数据 */
    // issue #116
    Object object = delegate.getObject(key);

    /* 2、如果二级缓存中不存在数据，就把对应的CacheKey添加到entriesMissedInCache中，标识一下，当前缓存中是没有当前CacheKey对应的数据的 */
    if (object == null) {
      entriesMissedInCache/* 缓存中丢失的条目 */.add(key);
    }

    /* 3、如果当前二级缓存被标识为已清除，那么直接返回null，即使从缓存中获取到了数据 */
    // issue #146
    if (clearOnCommit/* 提交时清除 */) {
      return null;
    }
    /* 4、如果当前二级缓存未被标识为已清除，那么返回从二级缓存中获取到的数据 */
    else {
      return object;
    }
  }

  /**
   * 将查询到的数据暂存到entriesToAddOnCommit，
   * 后续在sqlSession.close()的时候，会调用TransactionalCache#flushPendingEntries()，将entriesToAddOnCommit中的数据添加到二级缓存中，
   * 所以可以直接理解为就是往二级缓存中添加查询到的数据！
   *
   * @param key    CacheKey
   * @param object 查询到的结果（映射好的所有结果对象）
   */
  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  /**
   * 标识commit时需要"清除当前二级缓存"
   * （注意：⚠️这里只是标识一下在commit时需要清除当前二级缓存，并未真正清除！另外，如果当前二级缓存被标识为在commit时需要被清除，则也代表当前二级缓存中的数据是无效的、"当前二级缓存的数据"是作为已清除的数据、当前二级缓存是没有数据的，即使有数据，也代表没有数据，是空的）
   */
  @Override
  public void clear() {
    // 标识一下，commit时需要清除当前二级缓存；
    clearOnCommit/* commit时清除 */ = true;
    // 清空commit时准备往二级缓存中添加的数据
    entriesToAddOnCommit/* commit时添加的条目 */.clear();
  }

  /**
   * commit时对当前二级缓存的操作：
   * (1)如果当前二级缓存被标识为commit时需要清空，则清空当前二级缓存中的数据
   * (2)接着，将"commit时需要往二级缓存中添加的数据"，缓存到二级缓存
   * 题外：也就是说，上面清空完毕二级缓存，下面又接着添加数据，如果有的话
   * (3)重置当前二级缓存对应的TransactionalCache为刚创建时的样子
   */
  public void commit() {
    /* 1、如果当前二级缓存被标识为commit时需要清空，则清空当前二级缓存中的数据 */
    // 如果"clearOnCommit=true"，则在当前commit时，清除掉二级缓存数据
    if (clearOnCommit) {
      // 缓存采用的是装饰者模式，可以有层层包装，不过最底层的是PerpetualCache，也就是清除掉一个map里面的数据！
      delegate.clear();
    }

    /* 2、将"commit时需要往二级缓存中添加的数据"，缓存到二级缓存 */
    flushPendingEntries/* 刷新待处理条目 */();

    /* 3、重置当前二级缓存对应的TransactionalCache为刚创建时的样子 */
    reset();
  }

  /**
   * (1)移除二级缓存中"缺失的条目"，也就是：二级缓存中没有哪个CacheKey对应的数据，就移除这个CacheKey
   * (2)重置当前二级缓存的TransactionalCache为刚创建时的样子
   */
  public void rollback() {
    /* 1、移除二级缓存中"缺失的条目"，也就是：二级缓存中没有哪个CacheKey对应的数据，就移除这个CacheKey */
    unlockMissedEntries();

    /* 2、重置当前二级缓存的TransactionalCache为刚创建时的样子 */
    reset();
  }

  /**
   * 重置TransactionalCache为刚创建时的样子
   */
  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 1、将"commit时需要往二级缓存中添加的数据"，缓存到二级缓存
   * 2、"commit时需要往二级缓存中添加的数据"中不包含"缓存中缺失的条目"，则往二级缓存中添加"缺失的条目对应的CacheKey"，value值为null
   */
  private void flushPendingEntries/* 刷新待处理条目 */() {

    /* 1、将"commit时需要往二级缓存中添加的数据"，缓存到二级缓存 */
    // 将entriesToAddOnCommit中的数据，添加到对应的二级缓存中
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      // ⚠️往二级缓存中添加数据
      // SynchronizedCache
      delegate.putObject(entry.getKey(), entry.getValue());
    }

    /* 2、"commit时需要往二级缓存中添加的数据"中不包含"缓存中缺失的条目"，则往二级缓存中添加"缺失的条目对应的CacheKey"，value值为null */
    for (Object entry : entriesMissedInCache) {
      // "commit时添加的条目"中不包含"缓存中缺失的条目"，则往二级缓存中添加"缓存中缺失的条目对应的CacheKey"，value值为null
      if (!entriesToAddOnCommit.containsKey(entry)) {
        // ⚠️往二级缓存中添加数据
        // SynchronizedCache
        delegate.putObject(entry, null);
      }
    }

  }

  /**
   * 移除二级缓存中"缺失的条目"，也就是：二级缓存中没有哪个CacheKey对应的数据，就移除这个CacheKey
   */
  private void unlockMissedEntries/* 解锁缺失的条目 */() {
    // 遍历二级缓存中缺失的条目
    for (Object entry : entriesMissedInCache) {
      try {
        // 移除二级缓存中缺失的条目数据，也就是：二级缓存中没有哪个CacheKey对应的数据，就移除这个CacheKey
        delegate.removeObject(entry);
      } catch (Exception e) {
        // 向缓存适配器通知，回滚时出现意外异常。考虑将您的缓存适配器升级到最新版本。原因：+ e
        log.warn("Unexpected exception while notifying a rollback to the cache adapter. "
          + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}
