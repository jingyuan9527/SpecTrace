package com.spectrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spectrace.dto.HistoryPoint;
import com.spectrace.entity.DeviceParamValue;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface DeviceParamValueMapper extends BaseMapper<DeviceParamValue> {

    /**
     * 单一参数跨机型时序数据：指定品牌 + 参数，按发布时间排序。
     */
    @Select("""
            SELECT d.id AS deviceId, d.name AS deviceName, d.release_date AS releaseDate, v.param_value AS value
            FROM device_param_value v
            JOIN device d ON d.id = v.device_id
            WHERE d.brand_id = #{brandId}
              AND v.param_def_id = #{paramDefId}
              AND d.deleted = 0
            ORDER BY d.release_date ASC
            """)
    List<HistoryPoint> selectHistory(@Param("brandId") Long brandId, @Param("paramDefId") Long paramDefId);
}
