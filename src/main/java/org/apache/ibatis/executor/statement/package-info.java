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
/**
 * Statement handlers.
 */
package org.apache.ibatis.executor.statement;


/**
 *
 * sql语句
 *
 *
 *
 * 里面对于的都是handler，为什么要handler？虽然有对应的执行器了，但是执行器在调用具体执行的时候，说白了，还是在不同阶段的时候，通过不同的处理类，来执行我们具体的核心逻辑
 *
 *
 *
 */
