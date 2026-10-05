package guat.lxy.common.exception;

/**
 * 分类名重复（数据库 category.name 有 UNIQUE 约束）
 */
public class CategoryNameDuplicateException extends BaseException {

    public CategoryNameDuplicateException() {
        super("分类名已存在");
    }

    public CategoryNameDuplicateException(String msg) {
        super(msg);
    }
}