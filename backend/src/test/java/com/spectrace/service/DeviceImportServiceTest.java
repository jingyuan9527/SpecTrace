package com.spectrace.service;

import com.spectrace.common.BizException;
import com.spectrace.dto.DeviceImportResult;
import com.spectrace.dto.DeviceImportRow;
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.entity.ParamDef;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;

/**
 * 导入预览是脏数据的最后一道闸：表头兼容、行号定位与单位冲突都必须准确。
 * 这里只测 dryRun 分支，落库路径由 e2e 覆盖。
 */
@ExtendWith(MockitoExtension.class)
class DeviceImportServiceTest {

    @Mock
    private DeviceService deviceService;
    @Mock
    private DeviceSeriesService seriesService;
    @Mock
    private ParamService paramService;
    @Mock
    private BrandMapper brandMapper;
    @Mock
    private DeviceMapper deviceMapper;

    private DeviceImportService service;

    @BeforeEach
    void setUp() {
        service = new DeviceImportService(deviceService, seriesService, paramService, brandMapper, deviceMapper);

        ParamDef screen = new ParamDef();
        screen.setId(3L);
        screen.setParamKey("screen_size");
        screen.setParamName("屏幕尺寸");
        screen.setUnit("英寸");

        Brand apple = new Brand();
        apple.setId(1L);
        apple.setName("Apple");

        // 各用例共用，未触发的 stub 不应导致失败
        lenient().when(paramService.keyToDefMap()).thenReturn(Map.of("screen_size", screen));
        lenient().when(brandMapper.selectList(isNull())).thenReturn(List.of(apple));
        lenient().when(seriesService.nameIndex()).thenReturn(Map.of());
        lenient().when(deviceMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    @DisplayName("中文表头可识别，行号与解析结果准确")
    void parsesChineseHeader() {
        DeviceImportResult result = importAs("""
                机型名称,品牌,发布日期,屏幕尺寸
                iPhone 18,Apple,2026/9/20,6.3英寸
                """);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getValidCount()).isEqualTo(1);

        DeviceImportRow row = result.getRows().get(0);
        assertThat(row.getRowNo()).isEqualTo(2);
        assertThat(row.getReleaseDate()).isEqualTo("2026-09-20");
        assertThat(row.getParams()).containsEntry("screen_size", "6.3英寸");
        assertThat(row.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("品牌不存在时按行报错，不误判为可导入")
    void reportsUnknownBrand() {
        DeviceImportResult result = importAs("""
                name,brand
                iPhone 18,Nokia
                """);

        assertThat(result.getInvalidCount()).isEqualTo(1);
        assertThat(result.getRows().get(0).getErrors()).anyMatch(error -> error.contains("品牌不存在"));
    }

    @Test
    @DisplayName("文件内重复机型只标记后出现的行")
    void reportsDuplicateInsideFile() {
        DeviceImportResult result = importAs("""
                name,brand
                iPhone 18,Apple
                iPhone 18,Apple
                """);

        assertThat(result.getRows().get(0).getErrors()).isEmpty();
        assertThat(result.getRows().get(1).getErrors()).anyMatch(error -> error.contains("文件内存在重复机型"));
    }

    @Test
    @DisplayName("无法识别的列只在首行提示一次")
    void warnsUnknownColumnsOnce() {
        DeviceImportResult result = importAs("""
                name,brand,foo
                iPhone 18,Apple,x
                iPhone 19,Apple,y
                """);

        assertThat(result.getRows().get(0).getWarnings()).anyMatch(warning -> warning.contains("已忽略无法识别的列"));
        assertThat(result.getRows().get(1).getWarnings()).noneMatch(warning -> warning.contains("已忽略无法识别的列"));
    }

    @Test
    @DisplayName("单位与参数定义冲突时写入行级 warnings")
    void warnsUnitConflict() {
        DeviceImportResult result = importAs("""
                name,brand,屏幕尺寸
                iPhone 18,Apple,3600mAh
                """);

        assertThat(result.getRows().get(0).getWarnings())
                .anyMatch(warning -> warning.contains("单位与定义不一致"));
    }

    @Test
    @DisplayName("缺少必需列直接拒绝整个文件")
    void rejectsMissingRequiredHeaders() {
        assertThatThrownBy(() -> importAs("""
                机型名称,发布日期
                iPhone 18,2026-09-20
                """))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少必需列");
    }

    private DeviceImportResult importAs(String csv) {
        MockMultipartFile file = new MockMultipartFile(
                "file", "devices.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return service.importCsv(file, true, 1L, "tester");
    }
}
