package guat.lxy.common.exception;

/**
 * 用户被禁用（status = 0）
 */
public class UserDisabledException extends BaseException {

    public UserDisabledException() {
        super("用户被禁用");
    }

    public UserDisabledException(String msg) {
        super(msg);
    }
}
