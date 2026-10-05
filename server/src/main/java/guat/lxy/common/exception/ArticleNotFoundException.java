package guat.lxy.common.exception;

/**
 * 文章不存在（按 id 查不到记录）
 */
public class ArticleNotFoundException extends BaseException {

    public ArticleNotFoundException() {
        super("文章不存在");
    }

    public ArticleNotFoundException(String msg) {
        super(msg);
    }
}
