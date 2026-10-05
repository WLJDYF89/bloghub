package guat.lxy.mapper;

import guat.lxy.pojo.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("select * from user where username = #{userName}")
    User getByUsername(String userName);

    @Insert("insert into user (username, password, nickname, role_id, status, create_time, update_time) values (#{userName}, #{password}, #{nickname}, #{roleId}, #{status}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "userId")
    void insertUser(User user);

    /**
     * 管理员查询全部账号（含管理员自己，前端会按 role_id 打「管理员 / 作者」标签）
     * - select * 而不是逐列点名：项目里没有 schema.sql，无法保证 password 之外的列名
     *   （如 nickname/nick_name）与写法一致，select * 配合已开启的
     *   map-underscore-to-camel-case 可适配任意列名风格，避免 Unknown column 500
     * - password 字段虽会被查出，但 User.password 上有 @JsonIgnore，不会出现在 JSON 里
     */
    @Select("select * from user")
    List<User> getAllUsers();

    /** 找回密码：按用户名重置密码（调用方保证 userName 已存在、password 已加密） */
    @Update("update user set password = #{password}, update_time = now() where username = #{userName}")
    int resetPassword(String userName, String password);

}
