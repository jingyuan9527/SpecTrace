package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/** 生成社媒文案的入参：style 取 xiaohongshu / wechat。 */
@Data
public class SeriesSocialCopyRequest {

    /** 当前可见世代对应的机型；为空表示整条系列 */
    private List<Long> deviceIds;

    /** 当前勾选的参数；为空则后端按全系列机型组装 */
    private List<Long> paramDefIds;

    private String style;
}
