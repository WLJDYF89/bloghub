package guat.lxy.common.constant;

/**
 * Redis Key 前缀统一管理
 * 所有写入 Redis 的 key 都必须在这里声明前缀，避免 key 冲突、方便统一清理
 */
public class RedisKeyConstant {

    /**
     * JWT 登出黑名单：一旦登出，token 剩余有效期内都会被放到这个 key 下
     * 完整 key 例子：jwt:blacklist:eyJhbGciOiJIUzI1NiJ9.xxx.yyy
     * value：随便写（一般写 "1"），只要 key 存在就代表该 token 已作废
     * TTL：token 自身的剩余过期毫秒数（到点自动从 Redis 删掉，不占内存）
     */
    public static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:";

    private RedisKeyConstant() {
        // 工具类，禁止实例化
    }
}
