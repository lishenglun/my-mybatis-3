package com.msb.other.resultHandler;

import com.msb.other.resultHandler.bean.User;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/29 3:25 下午
 */
public class MyResultHandler implements ResultHandler<User> {

  @Override
  public void handleResult(ResultContext<? extends User> resultContext) {
    User resultObject = resultContext.getResultObject();
    Long id = resultObject.getId();
    //写数据处理业务
    System.out.println(id);
  }

}
