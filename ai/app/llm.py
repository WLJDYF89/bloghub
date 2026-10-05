import logging

from openai import AsyncOpenAI

from .config import settings

logger = logging.getLogger("bloghub.ai")

_client: AsyncOpenAI | None = None


def get_client() -> AsyncOpenAI:
    global _client
    if _client is None:
        _client = AsyncOpenAI(
            base_url=settings.llm_base_url,
            api_key=settings.llm_api_key,
            timeout=settings.llm_timeout_read,
        )
    return _client


async def stream_completion(messages: list[dict[str, str]], state: dict):
    """逐段产出模型输出的文本增量。

    state 是为了把 finish_reason 带回调用方（'stop' 正常 / 'length' 被截断），
    生成器本身只吐文本。
    """
    stream = await get_client().chat.completions.create(
        model=settings.llm_model,
        messages=messages,
        temperature=settings.llm_temperature,
        # max_tokens=settings.llm_max_tokens,
        stream=True,
    )

    async for chunk in stream:
        if not chunk.choices:
            continue
        choice = chunk.choices[0]
        if choice.finish_reason:
            state["finish"] = choice.finish_reason
        piece = choice.delta.content
        if piece:
            yield piece
