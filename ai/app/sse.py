import json


def sse_event(name: str, payload: dict) -> str:
    """一条具名 SSE 事件。data 必须是单行 JSON，否则前端切帧会出错。"""
    return f"event: {name}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


def sse_comment(text: str) -> str:
    """SSE 注释行。前端解析时直接忽略，只用来保活 / 探活。"""
    return f": {text}\n\n"


# 必须在调 LLM 之前吐出去：Spring 侧用同步 send() 做「Python 是否可用」的预检，
# 若等到首 token 才写响应头，那个 send() 会把 Tomcat 线程占住几十秒。
READY = sse_comment("ready")

# 每 10s 一条。上游静默时 Spring 阻塞在 read() 上察觉不到客户端断连，靠心跳把它唤醒。
HEARTBEAT = sse_comment("hb")
