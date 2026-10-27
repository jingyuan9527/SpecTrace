package com.spectrace.dto;

import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 机型详情响应。
 *
 * <p>此前用 {@code Map<String,Object>} 拼装，前端只能靠手写类型猜测结构，后端改键名也不会在编译期暴露。
 * 显式声明后，字段增删只有一处可改，接口文档也能反映真实结构。</p>
 */
@Data
public class DeviceDetailVO {

    private Device device;
    private Brand brand;
    /** 系列归属，未归类为 null（前端据此隐藏系列条） */
    private SeriesBriefVO series;
    /** 同系列已上架机型序列，口径与世代回顾一致 */
    private List<EvolutionDeviceVO> seriesDevices;
    /** paramKey -> 展示值 */
    private Map<String, String> params;
    /** paramKey -> 字段级溯源 */
    private Map<String, ParamMetaVO> paramMeta;
    private LocalDateTime lastUpdated;
    private List<DeviceVariantDTO> variants;
    /** 该机型各参数待核实的反馈数（key=paramKey），无反馈的参数不出现（T97） */
    private Map<String, Integer> paramFeedback;
}
