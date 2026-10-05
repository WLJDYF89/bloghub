package guat.lxy.common.exception;

/**
 * 文章 id 未传（为 null）
 */
public class ArticleIdRequiredException extends BaseException {

    public ArticleIdRequiredException() {
        super("文章id不能为空");
    }

    public ArticleIdRequiredException(String msg) {
        super(msg);
    }
}
