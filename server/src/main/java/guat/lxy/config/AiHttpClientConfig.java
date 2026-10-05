package guat.lxy.config;

import guat.lxy.common.properties.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * 转发 AI 流用的 HTTP 客户端。
 *
 * 用 JDK 自带的 java.net.http.HttpClient，不引 WebClient / OkHttp：
 * - WebClient 会把 Reactor + Netty 拖进这个纯 servlet 栈，为一条转发多一套并发模型
 * - OkHttp 只是省一点取消语法，不值得多一个依赖
 *
 * HTTP_1_1：到 Python 是明文，h2c 协商不上；显式声明 1.1 避免 h2 流控攒包导致「逐字出现」失效。
 * 虚拟线程 executor：SSE 是长时间占住线程的，不能让每个流吃掉一个平台线程。
 */
@Configuration
public class AiHttpClientConfig {

    @Bean
    public HttpClient aiHttpClient(AiProperties properties) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }
}
