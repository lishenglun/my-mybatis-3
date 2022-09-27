package com.msb.other.plugins.plugin;

import org.apache.ibatis.executor.HaHa;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import java.util.Properties;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 *
 *
 * 测试：是不是只要应用插件的对象，实现了对应的接口；然后我也拦截对应的接口。插件是不是就可以对应用插件的对象生效，进行动态代理？
 *
 * 例如：Executor会应用插件，我让CachingExecutor实现HaHa接口（自定义的），然后当前拦截器，就只拦截HaHa接口，看下，是否会对CachingExecutor进行动态代理，
 * 如果可以的话，就代表"应用插件的对象实现了什么接口，而我拦截对应的接口，就可以将插件作用于"应用插件的对象"之上"
 *
 * @date 2022/9/6 10:59 下午
 */
@Intercepts({
  // 拦截在 Executor 实例中所有的 “update” 方法调用
  @Signature(
    type = HaHa.class,
    method = "a",
    args = {}
  )})
public class ExamplePlugin3 implements Interceptor {

  private Properties properties = new Properties();

  /**
   * 拦截要拦截的目标方法
   *
   * @param invocation 里面包含：目标对象、目标方法、方法参数
   */
  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    // implement pre processing if need —— 根据需要进行预处理

    // 执行目标方法
    Object returnObject = invocation.proceed();

    // implement post processing if need —— 根据需要实施后处理
    return returnObject;
  }

  @Override
  public void setProperties(Properties properties) {
    this.properties = properties;
  }

}
