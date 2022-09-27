/*
 *    Copyright 2009-2022 the original author or authors.
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
package org.apache.ibatis.cursor;

import java.io.Closeable;

/**
 * 我在进行数据迭代的时候，可以一条条的取，也可以一批批的取，相当于，每次我在使用指针的时候，我必须要先把它给打开一个游标
 * （我在取数据的时候，必须先打开一个游标），相当于有了一个指针，当有了指针以后，就可以通过next()迭代到下一行记录里面，游标就相当于指针
 *
 * 纯数据库操作几乎不用，一般用于数据库的存储过程的数据迭代操作
 *
 * 在企业中，存储过程用的越来越少了，存储过程过程虽然可以很方便的解决一下需求问题，但是最大的问题是可维护性非常差，而且对于很多刚入行的程序员而言，他根本不懂存储过程，如果想把这个语法学下来，也很麻烦。
 * 修改存储过程很麻烦，特别是对于没有接触过的人而言，直接干懵了，所以不推荐使用
 *
 * Cursor contract to handle fetching items lazily using an Iterator.
 * Cursors are a perfect fit to handle millions of items queries that would not normally fit in memory.
 * If you use collections in resultMaps then cursor SQL queries must be ordered (resultOrdered="true")
 * using the id columns of the resultMap.
 *
 * @author Guillaume Darmont / guillaume@dropinocean.com
 */
public interface Cursor<T> extends Closeable, Iterable<T> {

  /**
   * @return true if the cursor has started to fetch items from database.
   */
  boolean isOpen();

  /**
   *
   * @return true if the cursor is fully consumed and has returned all elements matching the query.
   */
  boolean isConsumed();

  /**
   * Get the current item index. The first item has the index 0.
   *
   * @return -1 if the first cursor item has not been retrieved. The index of the current item retrieved.
   */
  int getCurrentIndex();
}
