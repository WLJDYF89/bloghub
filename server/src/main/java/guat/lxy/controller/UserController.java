package guat.lxy.controller;

import guat.lxy.common.result.Result;
import guat.lxy.pojo.dto.UserLoginDTO;
import guat.lxy.pojo.dto.UserRegisterDTO;
import guat.lxy.pojo.dto.UserResetPasswordDTO;
import guat.lxy.pojo.entity.User;
import guat.lxy.pojo.vo.UserLoginVO;
import guat.lxy.pojo.vo.UserRegisterVO;
import guat.lxy.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO){
        log.info("用户登录: {}", userLoginDTO);
        UserLoginVO loginVO = userService.login(userLoginDTO);
        return Result.success(loginVO);
    }

    @GetMapping("/info")
    public Result<User> getInfo(){
        log.info("获取用户信息");
        User user = userService.getUserInfo();
        return Result.success(user);
    }

    @PostMapping("/register")
    public Result<UserRegisterVO> register(@RequestBody UserRegisterDTO userRegisterDTO){
        log.info("用户注册: {}", userRegisterDTO);
        UserRegisterVO registerVO = userService.register(userRegisterDTO);
        return Result.success(registerVO);
    }

    @PostMapping("/logout")
    public Result logout(){
        log.info("用户退出登录");
        userService.logout();
        return Result.success();
    }

    /** 找回密码：按用户名重置密码（公开接口，无需登录） */
    @PostMapping("/reset-password")
    public Result resetPassword(@RequestBody UserResetPasswordDTO userResetPasswordDTO){
        log.info("找回密码: {}", userResetPasswordDTO);
        userService.resetPassword(userResetPasswordDTO);
        return Result.success();
    }

}
