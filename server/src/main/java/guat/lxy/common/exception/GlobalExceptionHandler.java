package guat.lxy.common.exception;

import guat.lxy.common.constant.MessageConstant;
import guat.lxy.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public Result<String> baseExceptionHandler(BaseException e) {
        log.error("业务异常: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    /**
     * 唯一约束冲突（如分类名重复）：
     * Service 层一般已捕获并转成带具体文案的 BaseException；
     * 这里是兜底，保证前端拿到的仍是中文人话而不是 SQL 报错原文
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<String> duplicateKeyExceptionHandler(DuplicateKeyException e) {
        log.error("唯一约束冲突: ", e);
        return Result.error(MessageConstant.NAME_DUPLICATE);
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<String> runtimeExceptionHandler(RuntimeException e) {
        log.error("运行时异常: ", e);
        return Result.error(e.getMessage() == null ? "系统异常" : e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<String> exceptionHandler(Exception e) {
        log.error("系统异常: ", e);
        return Result.error("系统异常");
    }
}
