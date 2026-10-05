package guat.lxy.common.exception;

/**
 * 非管理员访问管理员接口（/admin/**）
 */
public class AdminPermissionDeniedException extends BaseException {

    public AdminPermissionDeniedException() {
        super("无管理员权限");
    }

    public AdminPermissionDeniedException(String msg) {
        super(msg);
    }
}
