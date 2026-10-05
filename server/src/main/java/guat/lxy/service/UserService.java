package guat.lxy.service;

import guat.lxy.pojo.dto.UserLoginDTO;
import guat.lxy.pojo.dto.UserRegisterDTO;
import guat.lxy.pojo.dto.UserResetPasswordDTO;
import guat.lxy.pojo.entity.User;
import guat.lxy.pojo.vo.UserLoginVO;
import guat.lxy.pojo.vo.UserRegisterVO;


public interface UserService {

    /**
     * 用户登录
     * @param userLoginDTO
     * @return
     */
    UserLoginVO login(UserLoginDTO userLoginDTO);

    /**
     * 获取当前登录用户信息（身份来自 BaseContext，由 JwtInterceptor 统一鉴权后写入）
     * @return 当前登录用户
     */
    User getUserInfo();

    /**
     * 用户注册
     * @param userRegisterDTO
     * @return 新注册用户的 userId
     */
    UserRegisterVO register(UserRegisterDTO userRegisterDTO);

    /**
     * 当前登录用户退出登录（token 从 BaseContext 获取，拉黑由实现类完成）
     */
    void logout();

    /**
     * 找回密码：按用户名重置密码（无需登录，公开接口）
     * @param userResetPasswordDTO userName + 新密码
     */
    void resetPassword(UserResetPasswordDTO userResetPasswordDTO);
}
