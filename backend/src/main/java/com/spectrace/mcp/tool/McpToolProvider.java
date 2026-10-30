package com.spectrace.mcp.tool;

import java.util.List;

/**
 * 工具分组。按业务域拆类而不是一个巨型注册表，新增一组数据时不再改动注册中心。
 */
public interface McpToolProvider {

    List<McpTool> tools();
}
