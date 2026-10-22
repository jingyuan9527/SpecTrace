package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.BizException;
import com.spectrace.dto.DeviceImportResult;
import com.spectrace.dto.DeviceImportRow;
import com.spectrace.dto.DeviceSaveRequest;
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.entity.ParamDef;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 机型批量导入（CSV）。
 *
 * <p>CSV 约定：必需列 {@code name}（机型）与 {@code brand}（品牌名），可选 {@code releaseDate}；
 * 其余列名若与参数 Key 一致则作为该参数值，另支持 {@code params} 列以 {@code key=value;key=value} 形式批量填写。
 * 表头同时兼容中文（机型名称 / 品牌 / 发布日期）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceImportService {

    private static final int MAX_ROWS = 500;
    private static final Set<String> NAME_HEADERS = Set.of("name", "model", "机型名称", "机型");
    private static final Set<String> BRAND_HEADERS = Set.of("brand", "brandname", "品牌", "品牌名称");
    private static final Set<String> DATE_HEADERS = Set.of("releasedate", "date", "发布日期", "发布时间");
    private static final Set<String> PARAMS_HEADERS = Set.of("params", "参数");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"));

    private final DeviceService deviceService;
    private final ParamService paramService;
    private final BrandMapper brandMapper;
    private final DeviceMapper deviceMapper;

    /** 生成与当前参数定义同步的导入模板（UTF-8 BOM，Excel 直接可读）。 */
    public byte[] template() {
        List<ParamDef> defs = paramService.listDefs(null, 1);
        StringBuilder header = new StringBuilder("\uFEFFname,brand,releaseDate");
        for (ParamDef def : defs) {
            header.append(',').append(def.getParamKey());
        }
        header.append(",params\r\n");
        return header.toString().getBytes(StandardCharsets.UTF_8);
    }

    public DeviceImportResult importCsv(MultipartFile file, boolean dryRun, Long adminId, String adminName) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请上传 CSV 文件");
        }
        String raw = new String(readAll(file), StandardCharsets.UTF_8);
        if (raw.startsWith("\uFEFF")) {
            raw = raw.substring(1);
        }

        List<List<String>> table = parseCsv(raw);
        if (table.size() < 2) {
            throw new BizException("CSV 内容为空：至少需要表头和一行数据");
        }

        List<String> header = table.get(0).stream().map(String::trim).toList();
        Map<String, ParamDef> defByKey = paramService.keyToDefMap();
        Map<String, ParamDef> defByName = defByKey.values().stream()
                .filter(def -> StringUtils.hasText(def.getParamName()))
                .collect(Collectors.toMap(def -> def.getParamName().trim(), def -> def, (a, b) -> a));

        int nameIndex = indexOf(header, NAME_HEADERS);
        int brandIndex = indexOf(header, BRAND_HEADERS);
        if (nameIndex < 0 || brandIndex < 0) {
            throw new BizException("CSV 缺少必需列：name(机型名称) 与 brand(品牌)");
        }
        int dateIndex = indexOf(header, DATE_HEADERS);
        int paramsIndex = indexOf(header, PARAMS_HEADERS);

        // 列下标 -> 参数定义，用于逐列取值
        Map<Integer, ParamDef> paramColumns = new LinkedHashMap<>();
        List<String> unknownColumns = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            if (i == nameIndex || i == brandIndex || i == dateIndex || i == paramsIndex) {
                continue;
            }
            String key = header.get(i);
            if (!StringUtils.hasText(key)) {
                continue;
            }
            ParamDef def = defByKey.get(key);
            if (def == null) {
                def = defByName.get(key);
            }
            if (def != null) {
                paramColumns.put(i, def);
            } else {
                unknownColumns.add(key);
            }
        }

        List<Brand> brands = brandMapper.selectList(null);
        Map<String, Brand> brandByName = brands.stream()
                .collect(Collectors.toMap(b -> b.getName().trim().toLowerCase(Locale.ROOT), b -> b, (a, b) -> a));

        List<DeviceImportRow> rows = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();

        for (int r = 1; r < table.size(); r++) {
            List<String> cells = table.get(r);
            if (cells.stream().allMatch(cell -> !StringUtils.hasText(cell))) {
                continue;
            }
            if (rows.size() >= MAX_ROWS) {
                throw new BizException("单次最多导入 " + MAX_ROWS + " 行，请拆分文件");
            }

            DeviceImportRow row = new DeviceImportRow();
            row.setRowNo(r + 1);
            row.setName(cell(cells, nameIndex));
            row.setBrandName(cell(cells, brandIndex));

            if (r == 1 && !unknownColumns.isEmpty()) {
                row.getWarnings().add("已忽略无法识别的列：" + String.join("、", unknownColumns));
            }
            if (!StringUtils.hasText(row.getName())) {
                row.getErrors().add("机型名称不能为空");
            }
            if (!StringUtils.hasText(row.getBrandName())) {
                row.getErrors().add("品牌不能为空");
            }

            Brand brand = StringUtils.hasText(row.getBrandName())
                    ? brandByName.get(row.getBrandName().trim().toLowerCase(Locale.ROOT))
                    : null;
            if (StringUtils.hasText(row.getBrandName()) && brand == null) {
                row.getErrors().add("品牌不存在：" + row.getBrandName());
            }

            if (dateIndex >= 0) {
                String rawDate = cell(cells, dateIndex);
                if (StringUtils.hasText(rawDate)) {
                    LocalDate parsed = parseDate(rawDate);
                    if (parsed == null) {
                        row.getErrors().add("发布日期格式不正确：" + rawDate);
                    } else {
                        row.setReleaseDate(parsed.toString());
                    }
                }
            }

            for (Map.Entry<Integer, ParamDef> entry : paramColumns.entrySet()) {
                String value = cell(cells, entry.getKey());
                if (StringUtils.hasText(value)) {
                    row.getParams().put(entry.getValue().getParamKey(), value.trim());
                }
            }
            if (paramsIndex >= 0) {
                mergeInlineParams(row, cell(cells, paramsIndex), defByKey, defByName);
            }

            if (StringUtils.hasText(row.getName())) {
                String duplicateKey = (row.getBrandName() == null ? "" : row.getBrandName().trim().toLowerCase(Locale.ROOT))
                        + "|" + row.getName().trim().toLowerCase(Locale.ROOT);
                if (!seenInFile.add(duplicateKey)) {
                    row.getErrors().add("文件内存在重复机型");
                } else if (brand != null && existsInDb(brand.getId(), row.getName())) {
                    row.getErrors().add("库中已存在同名机型");
                }
            }

            rows.add(row);
        }

        DeviceImportResult result = new DeviceImportResult();
        result.setDryRun(dryRun);
        result.setTotal(rows.size());
        result.setRows(rows);
        result.setValidCount((int) rows.stream().filter(DeviceImportRow::isValid).count());
        result.setInvalidCount(result.getTotal() - result.getValidCount());

        if (dryRun) {
            log.info("机型导入预览: 共 {} 行，可导入 {} 行", result.getTotal(), result.getValidCount());
            return result;
        }

        int created = 0;
        for (DeviceImportRow row : rows) {
            if (!row.isValid()) {
                continue;
            }
            Brand brand = brandByName.get(row.getBrandName().trim().toLowerCase(Locale.ROOT));
            try {
                DeviceSaveRequest req = new DeviceSaveRequest();
                req.setBrandId(brand.getId());
                req.setName(row.getName().trim());
                req.setReleaseDate(StringUtils.hasText(row.getReleaseDate()) ? LocalDate.parse(row.getReleaseDate()) : null);
                req.setParams(row.getParams());
                req.setStatus(1);
                Device device = deviceService.save(req, adminId, adminName);
                row.setWarnings(List.of("已导入，机型 ID " + device.getId()));
                created++;
            } catch (Exception e) {
                log.error("导入机型失败: rowNo={} name={} brand={}", row.getRowNo(), row.getName(), row.getBrandName(), e);
                row.getErrors().add("导入失败：" + e.getMessage());
            }
        }
        result.setCreatedCount(created);
        result.setValidCount(created);
        result.setInvalidCount(result.getTotal() - created);
        log.info("机型导入完成: 共 {} 行，成功 {} 行，失败 {} 行", result.getTotal(), created, result.getTotal() - created);
        return result;
    }

    private void mergeInlineParams(DeviceImportRow row, String raw, Map<String, ParamDef> defByKey,
                                   Map<String, ParamDef> defByName) {
        if (!StringUtils.hasText(raw)) {
            return;
        }
        for (String pair : raw.split("[;&]")) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            int split = pair.indexOf('=');
            if (split <= 0) {
                row.getWarnings().add("忽略无法解析的参数片段：" + pair.trim());
                continue;
            }
            String key = pair.substring(0, split).trim();
            String value = pair.substring(split + 1).trim();
            ParamDef def = defByKey.get(key) != null ? defByKey.get(key) : defByName.get(key);
            if (def == null) {
                row.getWarnings().add("未知参数：" + key);
                continue;
            }
            row.getParams().put(def.getParamKey(), value);
        }
    }

    private boolean existsInDb(Long brandId, String name) {
        Long count = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getBrandId, brandId)
                .eq(Device::getName, name.trim()));
        return count != null && count > 0;
    }

    private LocalDate parseDate(String raw) {
        String value = raw.trim();
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其它常见格式
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 下一个格式
            }
        }
        return null;
    }

    private int indexOf(List<String> header, Set<String> candidates) {
        for (int i = 0; i < header.size(); i++) {
            if (candidates.contains(header.get(i).toLowerCase(Locale.ROOT))) {
                return i;
            }
        }
        return -1;
    }

    private String cell(List<String> cells, int index) {
        return index >= 0 && index < cells.size() ? cells.get(index).trim() : "";
    }

    private byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new BizException("读取上传文件失败：" + e.getMessage());
        }
    }

    /** 最小 CSV 解析：支持双引号包裹、转义引号与 CRLF。 */
    static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
