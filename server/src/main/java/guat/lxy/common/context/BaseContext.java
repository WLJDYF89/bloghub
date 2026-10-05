package guat.lxy.common.context;

/**
 * 当前登录用户上下文（基于 ThreadLocal）
 *
 * JwtInterceptor 统一鉴权通过后写入，请求结束（含异常路径）必须 clear，
 * 否则 Tomcat 线程池复用线程时会串号（A 用户的身份被 B 请求读到）。
 *
 * Service 层不再接收 token 参数，直接从这里取当前用户身份。
 */
public class BaseContext {

    /** 当前登录用户 ID（来自 JWT claims.userId） */
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    /** 当前登录用户名（来自 JWT claims.userName，已通过校验） */
    private static final ThreadLocal<String> CURRENT_USER_NAME = new ThreadLocal<>();

    /** 清洗后的当前请求 token（去掉 Bearer 前缀），登出拉黑时使用 */
    private static final ThreadLocal<String> CURRENT_TOKEN = new ThreadLocal<>();

    public static void setCurrentUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static Long getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void setCurrentUserName(String userName) {
        CURRENT_USER_NAME.set(userName);
    }

    public static String getCurrentUserName() {
        return CURRENT_USER_NAME.get();
    }

    public static void setCurrentToken(String token) {
        CURRENT_TOKEN.set(token);
    }

    public static String getCurrentToken() {
        return CURRENT_TOKEN.get();
    }

    /** 请求结束时统一清理，防止线程池复用导致身份串号 */
    public static void clearAll() {
        CURRENT_USER_ID.remove();
        CURRENT_USER_NAME.remove();
        CURRENT_TOKEN.remove();
    }
}
