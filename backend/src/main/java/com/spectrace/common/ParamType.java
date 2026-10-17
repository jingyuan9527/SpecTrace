package com.spectrace.common;

/**
 * 动态参数类型。value 统一以字符串存储，前端按此类型渲染与转换。
 */
public enum ParamType {
    text,
    number,
    image,
    date,
    select
}
