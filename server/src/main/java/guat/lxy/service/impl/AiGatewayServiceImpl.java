package guat.lxy.service.impl;

import guat.lxy.common.exception.AiServiceUnavailableException;
import guat.lxy.common.exception.ParamInvalidException;
import guat.lxy.common.properties.AiProperties;
import guat.lxy.pojo.dto.AiChatCommand;
import guat.lxy.service.AiGatewayService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@Slf4j
public class AiGatewayServiceImpl implements AiGatewayService {

    private static final int BUFFER_SIZE = 4096;

    private final HttpClient aiHttpClient;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public AiGatewayServiceImpl(HttpClient aiHttpClient, ObjectMapper objectMapper, AiProperties properties) {
        this.aiHttpClient = aiHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public HttpResponse<InputStream> open(AiChatCommand command) {
        byte[] payload = serialize(command);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/ai/chat"))
                .timeout(Duration.ofMillis(properties.getMaxDurationMs()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("X-Service-Token", properties.getSharedToken())
                .header("X-User-Name", command.userName() == null ? "" : command.userName())
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        HttpResponse<InputStream> response;
        try {
            response = aiHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            log.warn("连接 AI 服务失败: {}", e.getMessage());
            throw new AiServiceUnavailableException(describeConnectionFailure(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiServiceUnavailableException("AI 请求被中断，请重试");
        }

        if (response.statusCode() != 200) {
            throw new AiServiceUnavailableException(readUpstreamError(response));
        }
        return response;
    }

    @Override
    public void pump(HttpResponse<InputStream> upstream, HttpServletResponse response, OutputStream out) {
        // 方法上不声明 produces，否则异常路径上 Result 找不到能写 text/event-stream 的
        // converter 会变成 406。这里在写第一个字节之前手动设好。
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        try (InputStream in = upstream.body()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                // 逐块 flush。攒着就没有「文字逐个出现」的效果了。
                out.flush();
            }
        } catch (IOException e) {
            // 客户端关页面 / 点停止走这里。try-with-resources 的 close(in) 即取消上游请求，
            // HttpClient 会回收连接，不需要额外 cancel。
            log.debug("AI 流已关闭: {}", e.getMessage());
        }
    }

    private byte[] serialize(AiChatCommand command) {
        try {
            return objectMapper.writeValueAsBytes(command.body());
        } catch (JacksonException e) {
            throw new ParamInvalidException("AI 请求体无法序列化");
        }
    }

    /**
     * 非 200 时 body 通常很小（FastAPI 的错误 JSON），直接读完并关闭，
     * 免得把连接晾在那儿。
     */
    private String readUpstreamError(HttpResponse<InputStream> response) {
        int status = response.statusCode();
        try (InputStream in = response.body()) {
            String text = new String(in.readNBytes(2048), StandardCharsets.UTF_8);
            log.warn("AI 服务返回 {}: {}", status, text);
            return "AI 服务返回 " + status + "：" + text;
        } catch (IOException e) {
            return "AI 服务返回 " + status;
        }
    }

    private String describeConnectionFailure(IOException e) {
        if (e instanceof ConnectException
                || e instanceof HttpConnectTimeoutException
                || e.getCause() instanceof ConnectException) {
            return "AI 服务未启动，请先运行 ai 服务（uvicorn app.main:app --port 8000）";
        }
        return "AI 服务连接失败：" + e.getMessage();
    }
}
