package guat.lxy.service.impl;

import guat.lxy.common.constant.RoleIdConstant;
import guat.lxy.common.constant.MessageConstant;
import guat.lxy.common.context.BaseContext;
import guat.lxy.common.exception.*;
import guat.lxy.mapper.UserMapper;
import guat.lxy.pojo.dto.UserLoginDTO;
import guat.lxy.pojo.dto.UserRegisterDTO;
import guat.lxy.pojo.dto.UserResetPasswordDTO;
import guat.lxy.pojo.entity.User;
import guat.lxy.pojo.vo.UserLoginVO;
import guat.lxy.pojo.vo.UserRegisterVO;
import guat.lxy.service.PasswordService;
import guat.lxy.service.TokenService;
import guat.lxy.service.UserService;
import guat.lxy.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordService passwordService;

    @Override
    public UserLoginVO login(UserLoginDTO userLoginDTO) {
        String userName = userLoginDTO.getUserName();
        String password = userLoginDTO.getPassword();

        User user = userMapper.getByUsername(userName);

        if(user == null){
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 密码校验走 PasswordService：BCrypt 比对，并且如果库里还是老的 MD5 记录，
        // 会在验通过的同时把它升级成 BCrypt（用户无感，不需要改密码）
        if(!passwordService.verifyAndUpgradeIfLegacy(user, password)){
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if(user.getStatus() == 0){
            throw new UserDisabledException(MessageConstant.ACCOUNT_LOCKED);
        }

        String token = jwtUtils.generateToken((long) user.getUserId(), user.getUserName());

        return new UserLoginVO(user.getUserId(), user.getUserName(), user.getNickname(), token);
    }

    @Override
    public User getUserInfo() {
        // 用户名由 JwtInterceptor 统一鉴权后写入 BaseContext（三合一校验已完成）
        String userName = BaseContext.getCurrentUserName();

        User user = userMapper.getByUsername(userName);
        if(user == null){
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        return user;
    }

    @Override
    public UserRegisterVO register(UserRegisterDTO userRegisterDTO) {
        String userName = userRegisterDTO.getUserName();
        String password = userRegisterDTO.getPassword();
        String nickname = userRegisterDTO.getNickname();

        // 1. 参数校验：空值 + 长度
        if(userName == null || userName.trim().isEmpty()){
            throw new ParamInvalidException(MessageConstant.USERNAME_EMPTY);
        }
        if(password == null || password.trim().isEmpty()){
            throw new ParamInvalidException(MessageConstant.PASSWORD_EMPTY);
        }
        userName = userName.trim();
        password = password.trim();
        if(userName.length() < 3 || userName.length() > 20){
            throw new ParamInvalidException(MessageConstant.USERNAME_LENGTH_INVALID);
        }
        if(password.length() < 6){
            throw new ParamInvalidException(MessageConstant.PASSWORD_LENGTH_INVALID);
        }
        // 可选：限制 user_name 只能是中文/字母/数字/下划线
        if(!userName.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9_]{3,20}$")){
            throw new ParamInvalidException(MessageConstant.USERNAME_PATTERN_INVALID);
        }

        // 2. 用户名重复校验
        User existUser = userMapper.getByUsername(userName);
        if(existUser != null){
            throw new UserAlreadyExistsException(MessageConstant.USER_ALREADY_EXISTS);
        }

        // 3. 密码 BCrypt 加密（自带随机盐；登录时按 BCrypt 校验）
        String bcryptPassword = passwordService.encode(password);

        // 4. nickname 没传就默认等于 userName
        if(nickname == null || nickname.trim().isEmpty()){
            nickname = userName;
        }

        // 5. 构造 User 对象，补全所有默认字段
        User user = new User();
        user.setUserName(userName);
        user.setPassword(bcryptPassword);
        user.setNickname(nickname);
        user.setRoleId(RoleIdConstant.USER_ROLE_ID);
        user.setStatus(1);                         // 默认启用（不依赖数据库 DEFAULT）
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);                   // 不依赖数据库 DEFAULT CURRENT_TIMESTAMP
        user.setUpdateTime(now);                   // 不依赖数据库 DEFAULT CURRENT_TIMESTAMP

        // 6. 插入数据库，@Options 会把自增主键回填到 user.userId
        userMapper.insertUser(user);

        return new UserRegisterVO(user.getUserId(), user.getUserName(), user.getNickname());
    }

    /**
     * 找回密码：按用户名重置密码（公开接口，无需登录）
     * 校验链路与注册保持一致：非空 → 密码长度 → 用户存在 → BCrypt 加密落库
     *
     * ⚠ 注意：这个接口没有任何身份核验（不要旧密码、不要验证码），
     *   知道用户名就能改掉别人的密码，属于已知的安全缺口，改动密码算法不影响该结论。
     */
    @Override
    public void resetPassword(UserResetPasswordDTO userResetPasswordDTO) {
        String userName = userResetPasswordDTO.getUserName();
        String password = userResetPasswordDTO.getPassword();

        // 1. 参数校验
        if (userName == null || userName.trim().isEmpty()) {
            throw new ParamInvalidException(MessageConstant.USERNAME_EMPTY);
        }
        if (password == null || password.trim().isEmpty()) {
            throw new ParamInvalidException(MessageConstant.PASSWORD_EMPTY);
        }
        password = password.trim();
        if (password.length() < 6) {
            throw new ParamInvalidException(MessageConstant.PASSWORD_LENGTH_INVALID);
        }

        // 2. 用户必须存在（不暴露"账号是否存在"的区分度，统一提示与登录一致）
        User user = userMapper.getByUsername(userName.trim());
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 3. 新密码 BCrypt 加密后落库（与登录加密方式一致）
        String bcryptPassword = passwordService.encode(password);
        userMapper.resetPassword(user.getUserName(), bcryptPassword);
    }

    @Override
    public void logout() {
        // token 由 JwtInterceptor 统一鉴权时清洗后写入 BaseContext
        String token = BaseContext.getCurrentToken();

        // ========== 应用层 logout 完整闭环：先校验 → 再拉黑 ==========
        // 1. 先过统一校验：必须是一个「合法 + 没过期 + 还没被拉黑（还在登录状态）」的 token 才能登出
        //    这一步会自动拦截：
        //      - 假 token / 格式错 → LoginExpiredException("登录已过期，请重新登录")
        //      - 已过期 token    → LoginExpiredException("登录已过期，请重新登录")
        //      - 已经登出过的 token → LoginExpiredException("您已退出登录，请重新登录")（重复登出）
        //    用户就不会遇到"调 logout 返回 success 但实际上啥也没做"的情况了
        tokenService.validateAndThrow(token);        // 直接调用，不用变量接收

        // 2. 真正写 Redis 黑名单：TTL = token 剩余过期毫秒数
        //    Redis 未启动/连接异常时内部会抛 RedisServiceException（fail-fast，不降级），前端会收到"服务暂不可用"，
        //    不会出现"提示登出成功但 token 实际没拉黑"的假登出
        tokenService.addToBlacklist(token);

        // 若以后要扩展：在这里可以写登出日志、清该用户的在线状态缓存、踢其他端下线下等
    }
}
