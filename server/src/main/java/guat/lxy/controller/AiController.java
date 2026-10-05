package guat.lxy.controller;

import guat.lxy.common.context.BaseContext;
import guat.lxy.pojo.dto.AiChatCommand;
import guat.lxy.pojo.dto.AiChatDTO;
import guat.lxy.service.AiGatewayService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.http.HttpResponse;

/**
 * AI 写作助手的转发入口。
 *
 * 路径是 /ai/chat（前端调 /api/ai/chat，vite 代理会把 /api 前缀去掉）。
 * 它不在 WebConfig 的白名单里，所以自动要求登录 —— 鉴权完全复用现有的 JwtInterceptor，
 * Python 侧不解析 JWT。
 */
@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    @Autowired
    private AiGatewayService aiGatewayService;

    /**
     * 流式生成。返回 SSE，事件协议见 ai/app/main.py。
     *
     * 刻意不声明 produces = TEXT_EVENT_STREAM_VALUE：一旦声明，异常路径上
     * GlobalExceptionHandler 返回的 Result 就没有能写该 Content-Type 的 converter，
     * 会变成 406 而不是前端能识别的 {code:0, msg}。Content-Type 在 pump() 里手动设。
     */
    @PostMapping("/chat")
    public StreamingResponseBody chat(@RequestBody AiChatDTO dto, HttpServletResponse response) {
        log.info("AI 生成请求: action={}", dto.getAction());

        // 身份必须在当前请求线程同步取完 —— 异步线程读不到 ThreadLocal。
        AiChatCommand command = new AiChatCommand(dto, BaseContext.getCurrentUserName());
        // 立刻清掉：异步交接后 Tomcat 线程会先被线程池复用，而 afterCompletion 要等
        // 异步处理结束才回调，中间这段时间不清就会把当前用户身份带给下一个请求。
        BaseContext.clearAll();

        // 预检：Python 没启动 / 返回非 200 都在这里抛出，此时还没写任何响应字节，
        // 交给 GlobalExceptionHandler 仍能返回干净的 Result{code:0}
        HttpResponse<InputStream> upstream = aiGatewayService.open(command);

        return out -> aiGatewayService.pump(upstream, response, out);
    }
}
