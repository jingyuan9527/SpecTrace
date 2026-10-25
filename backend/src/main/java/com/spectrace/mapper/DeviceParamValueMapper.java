package com.spectrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spectrace.dto.DeviceLastUpdate;
import com.spectrace.dto.DeviceParamCount;
import com.spectrace.dto.HistoryPoint;
import com.spectrace.dto.ParamCoverage;
import com.spectrace.entity.DeviceParamValue;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
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

    /** 有任意参数值的机型数（数据健康度带参率分母侧统计） */
    @Select("SELECT COUNT(DISTINCT device_id) FROM device_param_value")
    long countDevicesWithParams();

    /** 各参数的有值机型数（参数覆盖排行，未出现在结果中的参数记 0） */
    @Select("""
            SELECT param_def_id AS paramDefId, COUNT(DISTINCT device_id) AS deviceCount
            FROM device_param_value
            GROUP BY param_def_id
            """)
    List<ParamCoverage> selectParamCoverage();

    /** 各机型的有值参数个数（缺参机型排行，未出现在结果中的机型记 0） */
    @Select("""
            SELECT device_id AS deviceId, COUNT(DISTINCT param_def_id) AS paramCount
            FROM device_param_value
            GROUP BY device_id
            """)
    List<DeviceParamCount> selectParamCountByDevice();

    /** 有参数但整体超过 N 天未更新的机型数（T87 阈值 180 天，T86/T106 质量观测） */
    @Select("""
            SELECT COUNT(*) FROM (
                SELECT device_id, MAX(updated_at) AS lastUpdated
                FROM device_param_value
                GROUP BY device_id
            )
            WHERE lastUpdated < #{cutoff}
            """)
    long countStaleDevices(@Param("cutoff") String cutoff);

    /** 各机型参数最近一次更新时间（系列陈旧度重抓圈定目标用，T87） */
    @Select("""
            <script>
            SELECT device_id AS deviceId, MAX(updated_at) AS lastUpdated
            FROM device_param_value
            WHERE device_id IN
            <foreach collection="deviceIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY device_id
            </script>
            """)
    List<DeviceLastUpdate> selectLastUpdateByDevice(@Param("deviceIds") Collection<Long> deviceIds);
}
