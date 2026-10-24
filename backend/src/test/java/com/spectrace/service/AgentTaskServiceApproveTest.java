package com.spectrace.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.spectrace.dto.ApproveRequest;
import com.spectrace.dto.DeviceSaveRequest;
import com.spectrace.entity.AgentPendingData;
import com.spectrace.entity.Device;
import com.spectrace.entity.AgentTask;
import com.spectrace.mapper.AgentPendingDataMapper;
import com.spectrace.mapper.AgentTaskMapper;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.task.TaskQueue;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审核通过链路：覆盖更新时回填字段决定老数据是否被清空（生产曾因漏 seriesId 清空系列归属）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentTaskServiceApproveTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AgentTask.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AgentPendingData.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Device.class);
    }

    @Mock
    private AgentTaskMapper taskMapper;
    @Mock
    private AgentPendingDataMapper pendingMapper;
    @Mock
    private BrandMapper brandMapper;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private TaskQueue taskQueue;
    @Mock
    private DeviceService deviceService;
    @Mock
    private ParamService paramService;
    @Mock
    private OperationLogService logService;
    @Mock
    private AgentTaskLogService taskLogService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private AgentTaskService service;

    @BeforeEach
    void setUp() {
        service = new AgentTaskService(taskMapper, pendingMapper, brandMapper, deviceMapper, taskQueue,
                deviceService, paramService, logService, taskLogService,
                new com.fasterxml.jackson.databind.ObjectMapper(), transactionTemplate);

        AgentPendingData pending = new AgentPendingData();
        pending.setId(9L);
        pending.setTaskId(19L);
        pending.setModelName("Xiaomi 17");
        pending.setBrandName("小米");
        pending.setStatus("PENDING_REVIEW");
        when(pendingMapper.selectById(9L)).thenReturn(pending);

        AgentTask task = new AgentTask();
        task.setId(19L);
        when(taskMapper.selectById(19L)).thenReturn(task);

        // 库中已有同品牌同名机型，且已归类到系列 5
        Device existing = new Device();
        existing.setId(11L);
        existing.setBrandId(2L);
        existing.setName("Xiaomi 17");
        existing.setSeriesId(5L);
        when(deviceMapper.selectOne(any())).thenReturn(existing);

        Device saved = new Device();
        saved.setId(11L);
        saved.setName("Xiaomi 17");
        when(deviceService.save(any(), anyLong(), anyString(), any())).thenReturn(saved);
    }

    @Test
    @DisplayName("覆盖更新时保持原系列归属，不被「取消归类」逻辑误清")
    void approveKeepsExistingSeriesOnOverwrite() {
        ApproveRequest req = new ApproveRequest();
        req.setBrandId(2L);
        req.setName("Xiaomi 17");
        req.setParams(Map.of("screen_size", "6.3"));

        service.approve(9L, req, 1L, "admin");

        ArgumentCaptor<DeviceSaveRequest> captor = ArgumentCaptor.forClass(DeviceSaveRequest.class);
        verify(deviceService).save(captor.capture(), eq(1L), eq("admin"), any());
        assertThat(captor.getValue().getId()).isEqualTo(11L);
        assertThat(captor.getValue().getSeriesId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("新机型审核通过不携带系列（保持未归类）")
    void approveNewDeviceStaysUnassigned() {
        when(deviceMapper.selectOne(any())).thenReturn(null);
        Device saved = new Device();
        saved.setId(30L);
        saved.setName("Xiaomi 18");
        when(deviceService.save(any(), anyLong(), anyString(), any())).thenReturn(saved);

        ApproveRequest req = new ApproveRequest();
        req.setBrandId(2L);
        req.setName("Xiaomi 18");
        req.setParams(Map.of("screen_size", "6.4"));

        service.approve(9L, req, 1L, "admin");

        ArgumentCaptor<DeviceSaveRequest> captor = ArgumentCaptor.forClass(DeviceSaveRequest.class);
        verify(deviceService).save(captor.capture(), eq(1L), eq("admin"), isNull());
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getSeriesId()).isNull();
    }
}
