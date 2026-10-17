"""采集 Agent 的工具集：搜索引擎 + 网页抓取。

以普通 async 函数实现，由 AgentScope 的 FunctionTool 适配器包装成工具，
名称取自函数名，描述取自 docstring，参数 Schema 取自类型注解。
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
from urllib.parse import quote_plus

import httpx

from config import settings

logger = logging.getLogger(__name__)

_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
)


async def web_search(query: str) -> str:
    """搜索机型参数相关的权威网页。

    Args:
        query: 搜索关键词，例如 "iPhone 17 参数 规格 官网"。

    Returns:
        搜索结果的 JSON 字符串，每项包含 title、url、snippet。
    """
    await asyncio.sleep(settings.crawl_interval)
    if settings.serper_api_key:
        return await _search_serper(query)
    return await _search_duckduckgo(query)


async def _search_serper(query: str) -> str:
    async with httpx.AsyncClient(timeout=20.0) as client:
        resp = await client.post(
            "https://google.serper.dev/search",
            headers={"X-API-KEY": settings.serper_api_key, "Content-Type": "application/json"},
            json={"q": query, "num": settings.search_result_limit},
        )
        resp.raise_for_status()
        data = resp.json()
    results = [
        {"title": item.get("title"), "url": item.get("link"), "snippet": item.get("snippet")}
        for item in data.get("organic", [])[: settings.search_result_limit]
    ]
    return json.dumps(results, ensure_ascii=False)


async def _search_duckduckgo(query: str) -> str:
    url = f"https://html.duckduckgo.com/html/?q={quote_plus(query)}"
    async with httpx.AsyncClient(timeout=20.0, headers={"User-Agent": _UA}, follow_redirects=True) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        html = resp.text

    pattern = re.compile(
        r'<a[^>]*class="result__a"[^>]*href="(?P<url>[^"]+)"[^>]*>(?P<title>.*?)</a>',
        re.DOTALL,
    )
    results = []
    for match in pattern.finditer(html):
        title = re.sub(r"<[^>]+>", "", match.group("title")).strip()
        results.append({"title": title, "url": match.group("url"), "snippet": ""})
        if len(results) >= settings.search_result_limit:
            break
    return json.dumps(results, ensure_ascii=False)


async def fetch_page(url: str) -> str:
    """用无头浏览器加载网页并提取正文文本。

    Args:
        url: 目标网页地址。

    Returns:
        网页正文纯文本（截断至 8000 字），失败时返回错误说明。
    """
    await asyncio.sleep(settings.crawl_interval)
    try:
        from playwright.async_api import async_playwright
    except ImportError:
        return "playwright 未安装，请执行: pip install playwright && playwright install chromium"

    try:
        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=True)
            page = await browser.new_page(user_agent=_UA)
            await page.goto(url, wait_until="domcontentloaded", timeout=30000)
            await page.wait_for_timeout(1500)
            text = await page.evaluate("document.body ? document.body.innerText : ''")
            await browser.close()
    except Exception as exc:  # noqa: BLE001 - 抓取失败需降级为文本提示交给 LLM 判断
        logger.warning("抓取网页失败 %s: %s", url, exc)
        return f"抓取失败: {exc}"

    text = re.sub(r"\n{3,}", "\n\n", text or "")
    return text[:8000]
