package guat.lxy.pojo.dto;

import lombok.Data;

/**
 * AI 生成请求。字段名与 Python 侧 app/schemas.py 的 Context 保持一致（camelCase）。
 */
@Data
public class AiChatDTO {

    /** CONTINUE / POLISH / REWRITE / TITLE / OUTLINE / TRANSLATE / CUSTOM */
    private String action;

    /** 仅 CUSTOM 用 */
    private String instruction = "";

    private Context context = new Context();

    @Data
    public static class Context {
        private String title = "";
        private String content = "";
        /** 编辑器里选中的那段；为空表示针对全文 */
        private String selection = "";
        private int selStart;
        private int selEnd;
        private String categoryName = "";
    }
}
