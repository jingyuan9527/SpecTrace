"""与 SpringBoot 业务后端的 HTTP 通信封装。

职责隔离：本模块只做「拉任务 / 报状态 / 回传结果 / 取 Schema」，
不直接访问任何数据库。
"""

from __future__ import annotations

import logging
from typing import Any, Optional

import httpx

from config import settings

logger = logging.getLogger(__name__)


class BackendClient:
    def __init__(self) -> None:
        self._base = settings.springboot_base_url.rstrip("/")
        self._headers = {"X-Agent-Token": settings.agent_token}
        self._client = httpx.AsyncClient(base_url=self._base, timeout=30.0)

    async def aclose(self) -> None:
        await self._client.aclose()

    async def fetch_schema(self) -> list[dict[str, Any]]:
        """获取参数抽取 Schema（启用中的参数定义）。"""
        resp = await self._client.get("/api/agent/schema", headers=self._headers)
        resp.raise_for_status()
        return resp.json().get("data", [])

    async def fetch_ai_config(self, model_id: int | None = None) -> dict[str, Any]:
        """获取实际使用的 AI 模型参数（provider / baseUrl / model / apiKey / temperature）。

        传入 model_id 则使用任务指定的模型，否则后端返回默认模型。
        """
        params = {"modelId": model_id} if model_id else None
        resp = await self._client.get("/api/agent/ai-config", headers=self._headers, params=params)
        resp.raise_for_status()
        return resp.json().get("data", {})

    async def pull_task(self) -> Optional[dict[str, Any]]:
        """HTTP 轮询模式下拉取下一个任务，无任务返回 None。"""
        resp = await self._client.get("/api/agent/tasks/pull", headers=self._headers)
        if resp.status_code == 204:
            return None
        resp.raise_for_status()
        return resp.json().get("data")

    async def mark_running(self, task_id: int) -> None:
        """通知后端任务已开始抓取（Redis 消费模式下使用）。"""
        await self._client.post(f"/api/agent/tasks/{task_id}/running", headers=self._headers)

    async def callback(self, payload: dict[str, Any]) -> None:
        """回传抓取结果，由 SpringBoot 写入待审核库。"""
        resp = await self._client.post("/api/agent/callback", headers=self._headers, json=payload)
        resp.raise_for_status()
