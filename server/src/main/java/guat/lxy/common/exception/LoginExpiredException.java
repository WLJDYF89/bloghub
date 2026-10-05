package guat.lxy.common.exception;

/**
 * 登录过期或 token 无效
 */
public class LoginExpiredException extends BaseException {

    public LoginExpiredException() {
        super("登录已过期");
    }

    public LoginExpiredException(String msg) {
        super(msg);
    }
}
