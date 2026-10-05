package guat.lxy.service;

import guat.lxy.pojo.dto.AiChatCommand;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpResponse;

/**
 * 把前端的 AI 生成请求转发到 Python 服务，并把 SSE 流原样透传回来。
 */
public interface AiGatewayService {

    /**
     * 建立到 Python 的连接并拿到响应头。
     *
     * 用同步 send() 而不是 sendAsync()：BodyHandlers.ofInputStream() 在「响应头到达」时
     * 就完成了，body 是惰性读的 —— 于是「Python 没启动 / 返回非 200」在这一步就能判定，
     * 可以干净地抛异常转成 Result{code:0}，而不用等流写到一半才发现。
     */
    HttpResponse<InputStream> open(AiChatCommand command);

    /** 逐块透传上游 SSE 字节流。 */
    void pump(HttpResponse<InputStream> upstream, HttpServletResponse response, OutputStream out);
}
