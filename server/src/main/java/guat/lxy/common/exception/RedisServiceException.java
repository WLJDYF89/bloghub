package guat.lxy.common.exception;

/**
 * Redis 服务不可用（未启动 / 连接超时 / 连接被拒绝等）
 *  token 黑名单校验、登出写黑名单都强依赖 Redis，Redis 挂了时直接拒绝请求，不做降级
 */
public class RedisServiceException extends BaseException {

    public RedisServiceException() {
        super("服务暂不可用，请稍后重试");
    }

    public RedisServiceException(String msg) {
        super(msg);
    }
}
