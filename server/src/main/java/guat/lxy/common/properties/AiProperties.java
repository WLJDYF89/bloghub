package guat.lxy.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python AI 服务（ai/ 目录，FastAPI）的对接配置。
 * 对应 application.yml 的 bloghub.ai.* 段。
 */
@Data
@Component
@ConfigurationProperties(prefix = "bloghub.ai")
public class AiProperties {

    /** Python AI 服务地址 */
    private String baseUrl = "http://127.0.0.1:8000";

    /** 服务间共享 token，与 ai/.env 的 SHARED_TOKEN 一致 */
    private String sharedToken = "dev-ai-token";

    /** 建连超时。用来把「Python 没启动」在 3 秒内变成一句中文提示，而不是挂住。 */
    private int connectTimeoutMs = 3000;

    /** 单次生成的整体上限。要大于模型最长可能的生成时间。 */
    private long maxDurationMs = 600_000L;
}
