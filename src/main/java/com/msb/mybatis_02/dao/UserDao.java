package com.msb.mybatis_02.dao;

import com.msb.mybatis_02.bean.Account;
import com.msb.mybatis_02.bean.User;
import org.apache.ibatis.annotations.One;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.RowBounds;

import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 我们定义的是接口，接口要跟配置文件里面的sql语句映射起来的！
 * @date 2022/8/8 10:05 下午
 */
public interface UserDao {

  // resultType
  User getUserByUserResultType(User user);

  // resultMap
  User getUserByUserResultMap(User user);

  User getAllUser();

  User getAllUserRowBounds(RowBounds rowBounds,Integer id);

  User getUserById(Integer id);

  int insert(User user);

  /* @One中resultMap属性的使用 */

  // 注意：⚠️使用注解的话，方法返回值就是@Results的type
  @Results(id = "accountMap",
    value = {
      @Result(id = true, column = "aid", property = "id"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "amount", property = "amount")
    }
  )
  @Select("")
  // @ResultType(Account.class)，返回值写void，然后写上这个，可以代替！
  Account accountMap();

  @Select("select u.id       as id,\n" +
    "       u.username as username,\n" +
    "       u.password as password,\n" +
    "       u.enable   as enable,\n" +
    "       a.id       as aid,\n" +
    "       a.user_id  as user_id,\n" +
    "       a.amount   as amount\n" +
    "from user u\n" +
    "         inner join account a on u.id = a.user_id;")
  @Results(id = "getUserListUserMap",
    value = {
      @Result(id = true, column = "id", property = "id"),
      @Result(column = "username", property = "username"),
      @Result(column = "password", property = "password"),
      @Result(column = "enable", property = "enable"),
      @Result(property = "account", javaType = Account.class, one = @One(resultMap = "accountMap"))
    })
  List<User> getUserList(User user,Integer id,Account account);

  //List<User> getUserListParam(User user,Integer id,Account account);

  //@Select("select * from user where id = #{uid} ")
  //@ResultMap("getUserListUserMap")
  //User findById(Integer userId);

  //@Insert("insert into user(username,sex,birthday,address)values(#{username},#{sex},#{birthday},#{address} )")
  //@SelectKey(keyColumn = "id", keyProperty = "id", resultType = Integer.class, before = false, statement = {"select last_insert_id()"})
  //int saveUser(User user);
  //
  //@Update("update user set username=#{username},address=#{address},sex=#{sex},birthday=#{birthday} where id=#{id}")
  //int updateUser(User user);
  //
  //@Delete("delete from user where id = #{uid} ")
  //int deleteUser(Integer userId);
  //
  ///**
  // * 查询使用聚合函数
  // */
  //@Select("select count(*) from user ")
  //int findTotal();
  //
  ///**
  // * 模糊查询
  // */
  //@Select("select * from user where username like #{username} ")
  //List<User> findByName(String name);



}
