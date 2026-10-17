"""采集 Agent 的运行时配置，全部来自环境变量。"""

import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


def _int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, default))
    except (TypeError, ValueError):
        return default


@dataclass(frozen=True)
class Settings:
    # SpringBoot 交互
    springboot_base_url: str = os.getenv("SPRINGBOOT_BASE_URL", "http://localhost:8080")
    agent_token: str = os.getenv("AGENT_TOKEN", "spectrace-agent-callback-token")

    # 任务获取
    task_mode: str = os.getenv("AGENT_TASK_MODE", "http").lower()  # http | redis
    redis_url: str = os.getenv("REDIS_URL", "redis://127.0.0.1:6379/0")
    queue_key: str = os.getenv("AGENT_QUEUE_KEY", "spectrace:agent:task")
    poll_interval: int = _int("POLL_INTERVAL_SECONDS", 5)

    # 大模型
    dashscope_api_key: str = os.getenv("DASHSCOPE_API_KEY", "")
    dashscope_model: str = os.getenv("DASHSCOPE_MODEL", "qwen-max")

    # 搜索
    serper_api_key: str = os.getenv("SERPER_API_KEY", "")
    search_result_limit: int = _int("SEARCH_RESULT_LIMIT", 5)

    # 抓取风控
    crawl_interval: int = _int("CRAWL_INTERVAL_SECONDS", 2)
    task_timeout: int = _int("TASK_TIMEOUT_SECONDS", 180)


settings = Settings()
