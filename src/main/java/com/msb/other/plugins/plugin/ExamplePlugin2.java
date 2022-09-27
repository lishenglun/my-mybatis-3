package com.msb.other.plugins.plugin;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import java.util.Properties;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 自定义拦截器，也就是插件
 * @date 2022/9/6 10:59 下午
 */
@Intercepts({
  // 拦截在 Executor 实例中所有的 “update” 方法调用
  @Signature(
    type = Executor.class,
    method = "update",
    args = {MappedStatement.class, Object.class})})
public class ExamplePlugin2 implements Interceptor {

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
