"""AgentScope 采集 Agent 工作进程入口。

启动后循环获取抓取任务并执行：
  Redis 模式：BLPOP 消费 spectrace:agent:task 队列
  HTTP 模式：轮询 GET /api/agent/tasks/pull
执行完成后通过 /api/agent/callback 把结构化结果回传 SpringBoot（写入待审核库）。

用法：
    python main.py            # 常驻消费
    python main.py --once     # 只处理一个任务后退出（便于联调）
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
from typing import Any, Optional

from backend_client import BackendClient
from collector_agent import collect
from config import settings

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
logger = logging.getLogger("spectrace.agent")


class CollectorWorker:
    def __init__(self) -> None:
        self.backend = BackendClient()
        self.schema_defs: list[dict[str, Any]] = []
        self.ai_config: dict[str, Any] = {}
        self._redis = None

    async def refresh_config(self, model_id: int | None = None) -> None:
        """从后端刷新参数 Schema 与 AI 模型参数（后台可随时改，Agent 下一轮生效）。

        model_id 为任务指定的模型，为空则后端使用默认模型。
        """
        self.schema_defs = await self.backend.fetch_schema()
        try:
            self.ai_config = await self.backend.fetch_ai_config(model_id)
            logger.info(
                "已加载 AI 模型：provider=%s model=%s baseUrl=%s",
                self.ai_config.get("provider"),
                self.ai_config.get("model"),
                self.ai_config.get("baseUrl"),
            )
        except Exception as exc:  # noqa: BLE001 - 未配置时回退 .env
            self.ai_config = {}
            logger.warning("拉取 AI 配置失败，回退 .env 配置：%s", exc)
        logger.info("已加载参数 Schema，共 %d 个字段", len(self.schema_defs))

    async def _next_task(self) -> Optional[dict[str, Any]]:
        if settings.task_mode == "redis":
            if self._redis is None:
                import redis.asyncio as aioredis

                self._redis = aioredis.from_url(settings.redis_url, decode_responses=True)
            item = await self._redis.blpop(settings.queue_key, timeout=settings.poll_interval)
            if item is None:
                return None
            _, raw = item
            payload = json.loads(raw)
            await self.backend.mark_running(payload["taskId"])
            return payload

        # HTTP 轮询模式
        payload = await self.backend.pull_task()
        if payload is None:
            await asyncio.sleep(settings.poll_interval)
        return payload

    async def _handle(self, payload: dict[str, Any]) -> None:
        task_id = payload.get("taskId")
        # 每个任务执行前刷新配置，后台改动无需重启 Agent；
        # 任务若指定了模型（aiModelId），则使用该模型，否则用默认模型
        await self.refresh_config(payload.get("aiModelId"))
        logger.info("开始采集 taskId=%s model=%s", task_id, payload.get("modelName"))
        try:
            result = await asyncio.wait_for(
                collect(payload, self.schema_defs, self.ai_config), timeout=settings.task_timeout
            )
        except asyncio.TimeoutError:
            result = {
                "taskId": task_id,
                "modelName": payload.get("modelName"),
                "brandName": payload.get("brandName"),
                "status": "FAILED",
                "error": f"抓取超时（>{settings.task_timeout}s）",
            }
        await self.backend.callback(result)
        logger.info("已回传结果 taskId=%s status=%s", task_id, result.get("status"))

    async def run(self, once: bool = False) -> None:
        await self.refresh_config()
        logger.info("采集 Agent 启动，任务模式=%s", settings.task_mode)
        while True:
            payload = await self._next_task()
            if payload:
                await self._handle(payload)
                if once:
                    break
            elif once:
                logger.info("当前无任务，退出")
                break
        await self.backend.aclose()


async def main() -> None:
    parser = argparse.ArgumentParser(description="SpecTrace 手机参数采集 Agent")
    parser.add_argument("--once", action="store_true", help="只处理一个任务后退出")
    args = parser.parse_args()

    worker = CollectorWorker()
    try:
        await worker.run(once=args.once)
    except KeyboardInterrupt:
        logger.info("收到中断信号，退出")


if __name__ == "__main__":
    asyncio.run(main())
