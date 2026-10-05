package guat.lxy.common.exception;

/**
 * 更新文章时没有任何可更新的字段被传入
 */
public class NothingToUpdateException extends BaseException {

    public NothingToUpdateException() {
        super("标题、内容、状态至少要修改一个");
    }

    public NothingToUpdateException(String msg) {
        super(msg);
    }
}
