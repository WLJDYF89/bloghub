package guat.lxy.common.exception;

/**
 * 无权操作他人文章（修改 / 删除）
 */
public class ArticlePermissionDeniedException extends BaseException {

    public ArticlePermissionDeniedException() {
        super("无权限操作他人文章");
    }

    public ArticlePermissionDeniedException(String msg) {
        super(msg);
    }
}
