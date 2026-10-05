import asyncio
import contextlib
import logging
import re
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import JSONResponse, StreamingResponse

from app.config import settings
from app.llm import stream_completion
from app.prompts import build_messages, sanitize
from app.schemas import ChatRequest
from app.sse import HEARTBEAT, READY, sse_event

logger = logging.getLogger("bloghub.ai")

# 上游静默多久吐一条心跳。也是「客户端已断连」的最长发现延迟。
HEARTBEAT_INTERVAL = 10.0

_KEY_RE = re.compile(r"sk-[A-Za-z0-9_\-]{8,}")


@asynccontextmanager
async def lifespan(_: FastAPI):
    # 缺 key 直接起不来，好过运行期第一次生成才报错
    if not settings.configured:
        raise RuntimeError(
            "LLM_API_KEY 未配置：请把 ai/.env.example 复制为 ai/.env，并填入 LLM_API_KEY"
        )
    logger.info(
        "BlogHub AI 已就绪 · model=%s · base_url=%s", settings.llm_model, settings.llm_base_url
    )
    yield


app = FastAPI(title="BlogHub AI", lifespan=lifespan)


@app.get("/ai/health")
async def health():
    if not settings.configured:
        return JSONResponse(
            {"configured": False, "model": settings.llm_model}, status_code=503
        )
    return {"configured": True, "model": settings.llm_model}


@app.post("/ai/chat")
async def chat(req: ChatRequest):
    logger.info("生成请求 action=%s 选中=%d 字", req.action.value, len(req.context.selection or ""))
    return StreamingResponse(
        _generate(req),
        media_type="text/event-stream; charset=utf-8",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )


async def _generate(req: ChatRequest):
    # 先吐一条注释行，让 Spring 侧的同步预检立刻拿到响应头
    yield READY

    # 模型调用在子任务里跑，主循环负责按固定间隔发心跳 ——
    # 否则上游静默期间 generator 没有任何 yield，客户端断连就发现不了。
    queue: asyncio.Queue = asyncio.Queue()
    state = {"finish": "stop"}

    async def pump():
        try:
            async for piece in stream_completion(build_messages(req), state):
                await queue.put(piece)
        except Exception as exc:  # noqa: BLE001 —— 任何上游异常都要转成 error 事件交给前端
            await queue.put(exc)
        finally:
            await queue.put(None)

    task = asyncio.create_task(pump())
    collected: list[str] = []
    failure: Exception | None = None

    try:
        while True:
            try:
                item = await asyncio.wait_for(queue.get(), timeout=HEARTBEAT_INTERVAL)
            except asyncio.TimeoutError:
                yield HEARTBEAT
                continue

            if item is None:
                break
            if isinstance(item, Exception):
                failure = item
                break

            collected.append(item)
            yield sse_event("delta", {"v": item})
    finally:
        # 客户端断连时 Starlette 会取消本生成器，顺手把模型请求一起掐掉
        if not task.done():
            task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await task

    if failure is not None:
        logger.warning("生成失败：%s", failure)
        yield sse_event("error", _describe(failure))
        return

    text = sanitize("".join(collected), req.action)
    if not text:
        logger.warning("模型返回了空内容 action=%s", req.action.value)
        yield sse_event(
            "error",
            {"code": "EMPTY_RESULT", "msg": "模型没有返回内容，请重试", "retryable": True},
        )
        return

    yield sse_event(
        "done",
        {
            "text": text,
            "finish": state["finish"],
            "truncated": state["finish"] == "length",
        },
    )


def _describe(exc: Exception) -> dict:
    """把上游异常翻译成前端能看懂的一句话。注意别把密钥漏出去。"""
    name = type(exc).__name__
    text = _KEY_RE.sub("sk-***", str(exc))
    lowered = text.lower()

    if "401" in text or "authentication" in lowered or "invalid_api_key" in lowered:
        return {
            "code": "NOT_CONFIGURED",
            "msg": "模型密钥无效或未配置，请检查 ai/.env 里的 LLM_API_KEY",
            "retryable": False,
        }
    if "429" in text or "rate" in lowered:
        return {"code": "RATE_LIMITED", "msg": "模型服务限流，请稍后重试", "retryable": True}
    if "timeout" in name.lower() or "timed out" in lowered or "timeout" in lowered:
        return {"code": "UPSTREAM_TIMEOUT", "msg": "模型响应超时，请重试", "retryable": True}
    if "connection" in lowered or "connect" in name.lower():
        return {
            "code": "UPSTREAM_UNREACHABLE",
            "msg": "连不上模型服务，请检查 LLM_BASE_URL 与网络",
            "retryable": True,
        }
    return {"code": "UPSTREAM_FAILED", "msg": f"模型调用失败：{text[:180]}", "retryable": True}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="127.0.0.1", port=settings.server_port)
