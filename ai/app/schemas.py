from enum import Enum

from pydantic import BaseModel, ConfigDict, Field


class Action(str, Enum):
    CONTINUE = "CONTINUE"
    POLISH = "POLISH"
    REWRITE = "REWRITE"
    TITLE = "TITLE"
    OUTLINE = "OUTLINE"
    TRANSLATE = "TRANSLATE"
    CUSTOM = "CUSTOM"


class Context(BaseModel):
    """编辑器当前状态。字段名与 Java 侧 AiChatDTO 的 camelCase 对齐。

    populate_by_name 让 sel_start / selStart 两种写法都能收，
    避免以后有人从浏览器手搓请求时踩名字。
    """

    model_config = ConfigDict(populate_by_name=True)

    title: str = ""
    content: str = ""
    selection: str = ""
    sel_start: int = Field(0, alias="selStart")
    sel_end: int = Field(0, alias="selEnd")
    category_name: str = Field("", alias="categoryName")


class ChatRequest(BaseModel):
    action: Action
    instruction: str = ""
    context: Context = Field(default_factory=Context)


class HealthResponse(BaseModel):
    configured: bool
    model: str
