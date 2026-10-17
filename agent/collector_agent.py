"""手机参数采集 Agent 定义（AgentScope 2.0）。

角色：手机数码参数专家。
链路：搜索 → 抓取网页 → LLM 结构化抽取 → 校验/附来源 → 输出 JSON。
输出约束：严格按系统参数 Schema 输出 JSON，禁止自由文本，必须附带来源链接。
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

from agentscope.agent import Agent
from agentscope.credential import DashScopeCredential, OpenAICredential
from agentscope.message import Msg
from agentscope.model import DashScopeChatModel, OpenAIChatModel
from agentscope.tool import FunctionTool, Toolkit

from config import settings
from tools import fetch_page, web_search

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """你是「手机数码参数专家」，负责从互联网检索并抽取手机机型的结构化参数。

工作方式：
1. 先用 web_search 工具搜索该机型的权威来源（品牌官网优先，其次知名数码媒体）。
2. 从搜索结果中挑选 2~4 个最权威的链接，用 fetch_page 工具抓取网页正文。
3. 综合多个来源抽取参数，遇到来源冲突时以官网为准，并在 confidence 字段说明。
4. 缺失的参数必须省略或置为 null，禁止编造。

输出要求（极其重要）：
- 只输出一个 JSON 对象，不要输出任何解释性文字、不要使用 Markdown 代码块。
- JSON 结构固定为：
  {
    "modelName": "机型名称",
    "brandName": "品牌名称",
    "sourceUrl": "主要来源链接",
    "confidence": "high | medium | low",
    "notes": "对冲突或存疑参数的说明",
    "params": { "<paramKey>": "<参数值，统一为字符串>" }
  }
- params 的键必须严格来自下方「参数 Schema」中的 param_key，不得自造键名。
- 参数值统一输出为字符串；带单位的数值只输出数字部分（单位由系统维护）。
"""


def _schema_prompt(schema_defs: list[dict[str, Any]]) -> str:
    """把后端返回的参数定义转成给 LLM 的 Schema 说明。"""
    lines = ["参数 Schema（param_key | 名称 | 类型 | 单位 | 枚举）："]
    for d in schema_defs:
        lines.append(
            " | ".join(
                [
                    str(d.get("paramKey", "")),
                    str(d.get("paramName", "")),
                    str(d.get("paramType", "text")),
                    str(d.get("unit") or "-"),
                    str(d.get("enumOptions") or "-"),
                ]
            )
        )
    return "\n".join(lines)


def _build_model(ai_config: dict[str, Any]) -> Any:
    """根据后台 AI 配置构建 AgentScope 大模型对象。

    provider=dashscope 使用 DashScopeChatModel；
    其余服务商（openai/deepseek/moonshot/ollama/vllm 等）统一走 OpenAI 兼容接口。
    未在后台配置时回退到 .env 中的 DASHSCOPE_* 配置。
    """
    provider = (ai_config.get("provider") or "dashscope").lower()
    api_key = ai_config.get("apiKey") or settings.dashscope_api_key
    model_name = ai_config.get("model") or settings.dashscope_model
    base_url = ai_config.get("baseUrl") or None
    temperature = ai_config.get("temperature")

    if not api_key:
        raise ValueError("未配置 AI API Key，请在后台「AI 配置」中填写")

    def _build(cls: Any, credential: Any, **extra: Any) -> Any:
        kwargs: dict[str, Any] = {"credential": credential, "model": model_name, **extra}
        if temperature is not None:
            kwargs["temperature"] = temperature
        try:
            return cls(**kwargs)
        except TypeError:
            kwargs.pop("temperature", None)
            return cls(**kwargs)

    if provider == "dashscope":
        cred_kwargs = {"api_key": api_key}
        if base_url:
            cred_kwargs["base_url"] = base_url
        return _build(DashScopeChatModel, DashScopeCredential(**cred_kwargs))

    cred_kwargs = {"api_key": api_key}
    if base_url:
        cred_kwargs["base_url"] = base_url
    return _build(OpenAIChatModel, OpenAICredential(**cred_kwargs))


def build_agent(schema_defs: list[dict[str, Any]], ai_config: dict[str, Any]) -> Agent:
    toolkit = Toolkit(tools=[FunctionTool(web_search), FunctionTool(fetch_page)])
    return Agent(
        name="phone_spec_collector",
        system_prompt=SYSTEM_PROMPT + "\n\n" + _schema_prompt(schema_defs),
        model=_build_model(ai_config),
        toolkit=toolkit,
    )


def _extract_text(msg: Any) -> str:
    if msg is None:
        return ""
    getter = getattr(msg, "get_text_content", None)
    if callable(getter):
        try:
            text = getter()
            if text:
                return text
        except Exception:  # noqa: BLE001
            pass
    return str(msg)


def _parse_json(text: str) -> dict[str, Any]:
    """从模型输出中提取 JSON 对象（容忍代码块与多余文字）。"""
    text = text.strip()
    text = re.sub(r"^```(?:json)?|```$", "", text, flags=re.MULTILINE).strip()
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        raise ValueError("模型未输出 JSON")
    return json.loads(match.group(0))


async def collect(payload: dict[str, Any], schema_defs: list[dict[str, Any]],
                  ai_config: dict[str, Any]) -> dict[str, Any]:
    """执行一次采集，返回可回传后端的结构化结果。

    返回结构对齐 SpringBoot /api/agent/callback 的请求体。
    """
    task_id = payload.get("taskId")
    model_name = payload.get("modelName", "")
    brand_name = payload.get("brandName") or ""

    agent = build_agent(schema_defs, ai_config)
    query = f"{brand_name} {model_name} 手机 完整参数 规格 官网".strip()
    prompt = (
        f"请检索并抽取机型「{model_name}」"
        + (f"（品牌：{brand_name}）" if brand_name else "")
        + " 的全部可获取参数，按系统 Schema 输出 JSON。"
    )
    observable = [query]

    try:
        msg = await agent(Msg("user", prompt, "user"))
        data = _parse_json(_extract_text(msg))
    except Exception as exc:  # noqa: BLE001
        logger.exception("采集失败 taskId=%s", task_id)
        return {
            "taskId": task_id,
            "modelName": model_name,
            "brandName": brand_name or None,
            "status": "FAILED",
            "error": f"{type(exc).__name__}: {exc}",
            "logs": json.dumps({"queries": observable}, ensure_ascii=False),
        }

    params = data.get("params") or {}
    allowed = {d.get("paramKey") for d in schema_defs}
    unknown = [k for k in params if k not in allowed]
    cleaned = {k: v for k, v in params.items() if k in allowed}

    return {
        "taskId": task_id,
        "modelName": data.get("modelName") or model_name,
        "brandName": data.get("brandName") or (brand_name or None),
        "sourceUrl": data.get("sourceUrl"),
        "status": "SUCCESS",
        "params": cleaned,
        "logs": json.dumps(
            {"queries": observable, "confidence": data.get("confidence"),
             "notes": data.get("notes"), "droppedUnknownKeys": unknown},
            ensure_ascii=False,
        ),
    }
