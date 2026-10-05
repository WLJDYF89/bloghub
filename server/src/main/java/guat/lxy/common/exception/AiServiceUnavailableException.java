package guat.lxy.common.exception;

/**
 * AI 服务不可用：没启动、连不上、或上游返回非 200。
 * 这类失败在「还没开始写响应体」的阶段抛出，会被 GlobalExceptionHandler 转成
 * Result{code:0, msg}，前端拿到的是一句能看懂的中文，而不是 5xx。
 */
public class AiServiceUnavailableException extends BaseException {

    public AiServiceUnavailableException(String msg) {
        super(msg);
    }
}
