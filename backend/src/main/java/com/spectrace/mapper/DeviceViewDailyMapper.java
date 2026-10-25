package com.spectrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spectrace.dto.DevicePv;
import com.spectrace.entity.DeviceViewDaily;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface DeviceViewDailyMapper extends BaseMapper<DeviceViewDaily> {

    /**
     * SQLite 原子自增：不存在则插入 1，存在则累加，避免先查后写的竞态。
     */
    @Insert("""
            INSERT INTO device_view_daily (device_id, view_date, pv) VALUES (#{deviceId}, #{viewDate}, 1)
            ON CONFLICT(device_id, view_date) DO UPDATE SET pv = pv + 1
            """)
    int upsertIncrement(@Param("deviceId") Long deviceId, @Param("viewDate") String viewDate);

    /** 近窗口期浏览量最高的机型 id，按 PV 倒序；无数据返回空列表，由调用方回落旧口径。 */
    @Select("""
            SELECT device_id FROM device_view_daily
            WHERE view_date >= #{sinceDate}
            GROUP BY device_id
            ORDER BY SUM(pv) DESC, device_id ASC
            LIMIT #{limit}
            """)
    List<Long> selectHotDeviceIds(@Param("sinceDate") String sinceDate, @Param("limit") int limit);

    /** 批量取一批机型近窗口期的浏览量合计，未出现在结果中的机型记 0。 */
    @Select("""
            <script>
            SELECT device_id AS deviceId, SUM(pv) AS pv
            FROM device_view_daily
            WHERE view_date &gt;= #{sinceDate}
              AND device_id IN
              <foreach collection="deviceIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY device_id
            </script>
            """)
    List<DevicePv> selectPvSince(@Param("sinceDate") String sinceDate, @Param("deviceIds") Collection<Long> deviceIds);

    /** 保留策略：按天聚合表只留最近 N 天。 */
    @Delete("DELETE FROM device_view_daily WHERE view_date < #{beforeDate}")
    int deleteBefore(@Param("beforeDate") String beforeDate);
}
