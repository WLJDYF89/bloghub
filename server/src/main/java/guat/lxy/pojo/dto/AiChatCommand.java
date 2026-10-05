package guat.lxy.pojo.dto;

/**
 * 转发到 Python 的完整请求：业务体 + 当前登录用户身份。
 * 身份在 Controller 里同步从 BaseContext 取出来，异步线程读不到 ThreadLocal。
 */
public record AiChatCommand(AiChatDTO body, String userName) {
}
