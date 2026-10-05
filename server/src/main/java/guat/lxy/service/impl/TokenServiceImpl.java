package guat.lxy.service.impl;

import guat.lxy.common.constant.MessageConstant;
import guat.lxy.common.constant.RedisKeyConstant;
import guat.lxy.common.exception.BaseException;
import guat.lxy.common.exception.LoginExpiredException;
import guat.lxy.common.exception.RedisServiceException;
import guat.lxy.service.TokenService;
import guat.lxy.utils.JwtUtils;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TokenServiceImpl implements TokenService {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public String validateAndThrow(String token) {
        // 1. 先统一洗 token（去空白、去 Bearer 前缀）。洗出来是 null/空 → 直接算未登录
        String clean = jwtUtils.cleanToken(token);
        if (clean == null || clean.isEmpty()) {
            throw new LoginExpiredException(MessageConstant.LOGIN_EXPIRED);
        }

        // 2. 解析 & 签名 & 过期统一在 try 里抓：任何 JWT 相关异常（格式错、签名被改、过期、结构坏）都统一当"登录已过期"
        long remain;
        String userName;
        try {
            // isTokenExpired 内部会再次 clean，但这里已经 clean 过也没关系，幂等
            if (jwtUtils.isTokenExpired(clean)) {
                throw new LoginExpiredException(MessageConstant.LOGIN_EXPIRED);
            }
            userName = jwtUtils.parseToken(clean).get("userName", String.class);
            remain = jwtUtils.getRemainingExpirationMillis(clean);
        } catch (LoginExpiredException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException 大家族：MalformedJwtException / ExpiredJwtException / UnsupportedJwtException
            // / SignatureException / PrematureJwtException 等
            log.warn("非法 token, 统一按登录过期处理: {}", e.getMessage());
            throw new LoginExpiredException(MessageConstant.LOGIN_EXPIRED);
        }

        // 3. 黑名单检查（登出过的 token 直接拒）
        if (remain > 0) {
            try {
                String blackKey = RedisKeyConstant.JWT_BLACKLIST_PREFIX + clean;
                Boolean blacklisted = stringRedisTemplate.hasKey(blackKey);
                if (Boolean.TRUE.equals(blacklisted)) {
                    // 🚨 注意：这是【业务异常】——必须抛出去让上层知道"你已经登出过了"
                    throw new LoginExpiredException(MessageConstant.LOGOUT_ALREADY);
                }
            } catch (BaseException be) {
                // 🚨 业务异常（LoginExpiredException / RedisServiceException 都是 BaseException 子类）直接 rethrow，绝对不能吞！
                throw be;
            } catch (RuntimeException redisEx) {
                // Redis 不可用（未启动 / 连接超时 / 连接被拒绝 —— RedisConnectionFailureException / RedisSystemException 等）：
                // 🚨 不做降级跳过！黑名单校验是安全环节，Redis 挂了就无法确认 token 是否已登出，
                //    必须 fail-fast 直接拒绝请求，否则已登出的 token 仍能访问（越权风险）
                log.error("Redis 黑名单校验失败（Redis 未启动/连接异常），拒绝本次请求。err={}", redisEx.getMessage());
                throw new RedisServiceException(MessageConstant.REDIS_SERVICE_ERROR);
            }
        }

        return userName;
    }

    @Override
    public void addToBlacklist(String token) {
        String clean = jwtUtils.cleanToken(token);
        if (clean == null || clean.isEmpty()) {
            return;
        }

        long remain;
        try {
            remain = jwtUtils.getRemainingExpirationMillis(clean);
        } catch (JwtException | IllegalArgumentException e) {
            // token 本身就解析不了（比如前端传了个假的来登出），没必要写 Redis
            log.warn("登出的 token 解析失败，跳过黑名单写入: {}", e.getMessage());
            return;
        }

        // token 已经自然过期（remain=0），没必要浪费 Redis 内存
        if (remain <= 0) {
            return;
        }

        try {
            String blackKey = RedisKeyConstant.JWT_BLACKLIST_PREFIX + clean;
            // value 写啥无所谓，只要 key 存在就代表被拉黑；TTL = 剩余毫秒数，到点自动删
            stringRedisTemplate.opsForValue().set(blackKey, "1", remain, TimeUnit.MILLISECONDS);
        } catch (BaseException be) {
            // 业务异常不吞，直接抛
            throw be;
        } catch (RuntimeException redisEx) {
            // Redis 不可用：黑名单写不进去意味着登出在服务端不成立（token 仍然有效），
            // 必须抛异常告知前端"登出失败"，不能静默返回成功造成"假登出"
            log.error("写入 Redis 登出黑名单失败（Redis 未启动/连接异常），登出失败。err={}", redisEx.getMessage());
            throw new RedisServiceException(MessageConstant.REDIS_SERVICE_ERROR);
        }
    }
}

