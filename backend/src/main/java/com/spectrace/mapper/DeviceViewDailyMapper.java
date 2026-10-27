package com.spectrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spectrace.dto.DailyPv;
import com.spectrace.dto.DevicePv;
import com.spectrace.dto.TopDevicePv;
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

    /** 按批次增量累加：定时 flush 一次写一条，替代每次访问一条 UPDATE。 */
    @Insert("""
            INSERT INTO device_view_daily (device_id, view_date, pv) VALUES (#{deviceId}, #{viewDate}, #{delta})
            ON CONFLICT(device_id, view_date) DO UPDATE SET pv = pv + #{delta}
            """)
    int upsertIncrementBy(@Param("deviceId") Long deviceId, @Param("viewDate") String viewDate,
                          @Param("delta") int delta);

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

    /** 全站日 PV 序列（T103 流量趋势）。 */
    @Select("""
            SELECT view_date AS date, SUM(pv) AS pv
            FROM device_view_daily
            WHERE view_date >= #{sinceDate}
            GROUP BY view_date
            ORDER BY view_date ASC
            """)
    List<DailyPv> selectDailyPv(@Param("sinceDate") String sinceDate);

    /** 窗口期 PV TOP 机型（联 device 取名，软删机型自然被过滤出排行）。 */
    @Select("""
            SELECT v.device_id AS deviceId, d.name AS deviceName, SUM(v.pv) AS pv
            FROM device_view_daily v
            JOIN device d ON d.id = v.device_id AND d.deleted = 0
            WHERE v.view_date >= #{sinceDate}
            GROUP BY v.device_id
            ORDER BY pv DESC, v.device_id ASC
            LIMIT #{limit}
            """)
    List<TopDevicePv> selectTopDevices(@Param("sinceDate") String sinceDate, @Param("limit") int limit);

    /** 指定日期区间的 PV 合计（周环比用）。 */
    @Select("""
            SELECT COALESCE(SUM(pv), 0) FROM device_view_daily
            WHERE view_date >= #{fromDate} AND view_date < #{toDate}
            """)
    long sumPvBetween(@Param("fromDate") String fromDate, @Param("toDate") String toDate);

    /** 保留策略：按天聚合表只留最近 N 天。 */
    @Delete("DELETE FROM device_view_daily WHERE view_date < #{beforeDate}")
    int deleteBefore(@Param("beforeDate") String beforeDate);
}
