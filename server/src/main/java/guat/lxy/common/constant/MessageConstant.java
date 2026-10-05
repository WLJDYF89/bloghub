package guat.lxy.common.constant;

public class MessageConstant {

    // ==================== 用户 / 登录 ====================
    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_NOT_FOUND = "找不到该用户";
    public static final String ACCOUNT_LOCKED = "用户被禁用";
    public static final String USER_ALREADY_EXISTS = "用户已存在";
    public static final String LOGIN_EXPIRED = "登录已过期";
    public static final String LOGOUT_ALREADY = "您已退出登录，请重新登录";

    // ==================== 参数校验 ====================
    public static final String USERNAME_EMPTY = "用户名不能为空";
    public static final String PASSWORD_EMPTY = "密码不能为空";
    public static final String USERNAME_LENGTH_INVALID = "用户名长度必须在3-20个字符之间";
    public static final String PASSWORD_LENGTH_INVALID = "密码长度不能少于6位";
    public static final String USERNAME_PATTERN_INVALID = "用户名只能包含中文、字母、数字和下划线";

    // ==================== 文章 ====================
    public static final String ARTICLE_ID_REQUIRED = "文章id不能为空";
    public static final String NOTHING_TO_UPDATE = "标题、内容、状态至少要修改一个";
    public static final String ARTICLE_NOT_FOUND = "文章不存在";
    public static final String ARTICLE_UPDATE_PERMISSION_DENIED = "无权限修改他人文章";
    public static final String ARTICLE_DELETE_PERMISSION_DENIED = "无权限删除他人文章";

    // ==================== 分类 ====================
    public static final String CATEGORY_NAME_EMPTY = "分类名不能为空";
    public static final String CATEGORY_ID_REQUIRED = "分类id不能为空";
    public static final String CATEGORY_NAME_DUPLICATE = "分类名已存在";
    public static final String NAME_DUPLICATE = "名称已存在";

    // ==================== 管理员 ====================
    public static final String ADMIN_PERMISSION_DENIED = "无管理员权限";

    // ==================== 系统 / 中间件 ====================
    public static final String REDIS_SERVICE_ERROR = "服务暂不可用，请稍后重试";

}

