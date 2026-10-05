package guat.lxy.service;

/**
 * Token 统一校验 + 黑名单管理
 * 所有需要登录态的接口（/user/info、/article/add /update /delete /my ...）都走这里，
 * 不要再各自写 isTokenExpired / parseToken，避免重复代码和漏写黑名单校验
 */
public interface TokenService {

    /**
     * 校验 token 是否可用，不通过直接抛业务异常
     * 校验顺序：① Bearer 清洗 + 签名/格式校验 → ② 是否过期 → ③ 是否被登出黑名单
     *
     * @param token 前端 Authorization Header 传的原始值（可能带 "Bearer " 前缀）
     * @return 当前登录用户的 userName（从 token 的 claim 里取，省得上层再解析一次）
     * @throws guat.lxy.common.exception.LoginExpiredException 过期 / 黑名单命中 / token 非法，统一抛登录过期类异常
     */
    String validateAndThrow(String token);

    /**
     * 登出：把当前 token 加入 Redis 黑名单，TTL = token 剩余有效毫秒数
     * - token 已经过期 → 不写 Redis（省内存，反正已经没法用了）
     * - token 还有效 → 写入后，到 token 自然过期那一刻 Redis key 自动删除
     */
    void addToBlacklist(String token);
}
