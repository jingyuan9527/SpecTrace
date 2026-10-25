package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 待审核数据视图对象，params 为解析后的键值对。
 */
@Data
public class PendingDataVO {
    private Long id;
    private Long taskId;
    private String modelName;
    private String brandName;
    private String sourceUrl;
    private String status;

    /** AI 预审风险等级（T86）：HIGH / MEDIUM / NORMAL / PASS，仅待审核状态计算 */
    private String riskLevel;

    /** 官方产品图 URL（T90，可空）：审核页预览用，通过后异步下载本地化 */
    private String imageUrl;
    private LocalDateTime createdAt;
    private Map<String, Object> params;
    /** paramKey -> 采集置信度（0~1），未提供时为空 */
    private Map<String, Double> paramConfidence;

    /** paramKey -> 单位冲突提示，审核页据此提醒修正 */
    private Map<String, String> paramWarnings;

    /** 采集到的存储版本与官方发布价 */
    private List<DeviceVariantDTO> variants;
    /** 每个参数对应的中文名与分组，便于前端展示 */
    private Map<String, String> paramNames;

    /** true 表示库中尚无同品牌同名机型，审核页按「新机型」展示 */
    private Boolean newDevice;

    /** 已入库的同品牌同名机型 id，用于 diff 与覆盖更新 */
    private Long existingDeviceId;

    /** 已入库机型现有参数（paramKey -> value），供审核页标出变化项 */
    private Map<String, String> existingParams;

    /** 已入库机型现有存储版本，供审核页对比价格 */
    private List<DeviceVariantDTO> existingVariants;
}
