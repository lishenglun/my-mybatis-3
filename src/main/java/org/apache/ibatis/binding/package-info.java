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
/**
 * Binds mapper interfaces with mapped statements.
 */
package org.apache.ibatis.binding;


/**
 * mapper一般写的都是接口，接口中的方法是没有做任何实现的，所以想要只定义一个接口，就拥有用具体的实现，就可以调用接口中的方法的话，只能用动态代理的方式实现，创建接口的代理对象，有了对象，才能调用接口的方法！
 *
 * 题外：我们可以不用动态代理的方式，创建接口对象；而是直接new接口，创建接口的对象，有了对象，也能调用接口的方法。但是我们new的接口对象，是没有具体的mybatis逻辑的！是无效的！
 * 当然我们可以在new接口的对象中写上mybatis的逻辑，但是这样是复杂的，繁琐的，而且每创建一个接口对象都要写上相同的繁琐的逻辑，
 * 当然这个也可以定义一个接口的类模版进行代替，下次直接new对应的类即可，但是每来一个接口，都要写具体的实现类，也是很繁琐的，
 * 有没有什么方式，可以简化这些操作呢？让用户只定义一个接口，就可以拥有具体的对象，去调用接口方法；且对象中包含了mybatis的逻辑？
 * 只能是动态代理的方式，生成接口的代理对象，并且植入mybatis的逻辑
 *
 */
