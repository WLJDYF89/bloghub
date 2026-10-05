import re

from .schemas import Action, ChatRequest

# 七个动作共用的守卫后缀。后端存的是纯文本、不承诺 markdown，
# 所以模型输出必须能直接原地粘回 textarea —— 任何解释、前言、代码围栏都会变成正文垃圾。
GUARD = (
    "只输出正文本身。不要解释、不要前言、不要结语、不要 Markdown 代码围栏、"
    "不要 # 标题符号、不要自称 AI。保持原作者的语感和人称。"
)

# {Action: (system, user 模板)}。模板里只能用 build_messages 提供的占位符。
ACTIONS: dict[Action, tuple[str, str]] = {
    Action.CONTINUE: (
        "你是一位中文博客写作助手，负责顺着作者已有的文字往下写。",
        "标题：{title}\n分类：{category}\n\n"
        "已有正文：\n{content}\n\n"
        "请顺着上面的行文继续往下写一段，衔接自然，不要重复已有内容，不要另起标题。",
    ),
    Action.POLISH: (
        "你是一位中文文字编辑，负责润色而不改变原意与信息量。",
        "标题：{title}\n\n待润色文字：\n{target}\n\n"
        "请润色这段文字：修正语病、理顺节奏、去掉冗余，但不要增删事实、不要改变作者语气。",
    ),
    Action.REWRITE: (
        "你是一位中文写作助手，负责换一种说法重写，保持信息量不变。",
        "标题：{title}\n\n待改写文字：\n{target}\n\n"
        "请改写这段文字：换一个角度或句式重新表达，意思保持一致，长度相当。",
    ),
    Action.TITLE: (
        "你是一位中文博客编辑，擅长起克制、具体、不标题党的标题。",
        "正文：\n{content}\n\n请给出一个标题，直接输出标题本身。",
    ),
    Action.OUTLINE: (
        "你是一位中文博客写作助手，负责给一篇文章搭大纲。",
        "标题：{title}\n已有正文：\n{content}\n\n"
        "请给这篇还没写完的文章列一份写作大纲，逐行输出，每行一个要点，"
        "行首用「一、二、三、」这样的中文序号，不要用其它符号。",
    ),
    Action.TRANSLATE: (
        "你是一位中英互译译者。",
        "待翻译文字：\n{target}\n\n"
        "请翻译这段文字：中文译成英文，其它语言译成中文。只输出译文。",
    ),
    Action.CUSTOM: (
        "你是一位中文博客写作助手。",
        "标题：{title}\n\n正文：\n{content}\n\n{focus}作者的要求：{instruction}",
    ),
}


def _or(value: str, fallback: str) -> str:
    text = (value or "").strip()
    return text if text else fallback


def build_messages(req: ChatRequest) -> list[dict[str, str]]:
    ctx = req.context
    system, template = ACTIONS[req.action]

    # 选中了就用选中的那段，没选就针对全文 —— 与前端「替换选中 / 插入到光标处」的语义一致
    target = _or(ctx.selection, ctx.content)

    # 正文为空时的续写：别写「已有正文：（空）」，直接让它从头开个头
    if req.action is Action.CONTINUE and not (ctx.content or "").strip():
        content_block = "（正文还是空的，请根据标题直接写一个开头）"
    else:
        content_block = _or(ctx.content, "（还没有正文）")

    focus = ""
    if (ctx.selection or "").strip():
        focus = "以下是作者选中的片段，你的处理只针对它：\n" + ctx.selection.strip() + "\n\n"

    user = template.format(
        title=_or(ctx.title, "（未填写）"),
        content=content_block,
        target=_or(target, "（没有可处理的文字）"),
        category=_or(ctx.category_name, "未归类"),
        instruction=_or(req.instruction, "（作者没写具体要求，请自行判断最值得改的地方）"),
        focus=focus,
    )

    # 除自定义外的动作也允许作者补一句要求（留空则完全按模板来）
    extra = (req.instruction or "").strip()
    if extra and req.action is not Action.CUSTOM:
        user += "\n\n作者的额外要求，请在不偏离上面任务的前提下遵守：" + extra

    return [
        {"role": "system", "content": system + GUARD},
        {"role": "user", "content": user},
    ]


_FENCE_RE = re.compile(r"^\s*```[a-zA-Z0-9_-]*\s*\n?(.*?)\n?\s*```\s*$", re.S)
_PREAMBLE_RE = re.compile(r"^\s*(好的|当然|没问题|以下是|这是)[^\n]{0,20}[：:]\s*\n?")
_BLANK_RE = re.compile(r"\n{3,}")


def sanitize(text: str, action: Action) -> str:
    """把模型输出洗成能直接粘回 textarea 的纯文本。"""
    out = (text or "").strip()

    fenced = _FENCE_RE.match(out)
    if fenced:
        out = fenced.group(1).strip()

    out = _PREAMBLE_RE.sub("", out)
    out = _BLANK_RE.sub("\n\n", out).strip()

    if action is Action.TITLE:
        out = out.split("\n")[0].strip()
        out = out.strip("# \t\"'“”「」『』")

    return out
