package com.spectrace.controller;

import com.spectrace.common.BizException;
import com.spectrace.common.R;
import com.spectrace.dto.UploadResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传（机型图片、品牌 Logo）。
 *
 * <p>安全约束：只接受真实内容为 JPEG/PNG/WebP/GIF 的图片（按文件头判断，不信任客户端 Content-Type），
 * 扩展名由服务端判定，大小受 {@code spectrace.upload.max-bytes} 限制。
 * 本地开发写入 ./uploads 目录；生产可替换为对象存储实现。</p>
 */
@Slf4j
@Tag(name = "后台-文件上传")
@RestController
@RequestMapping("/api/admin/files")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class FileController {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    @Value("${spectrace.upload-dir:uploads}")
    private String uploadDir;

    @Value("${spectrace.upload.max-bytes:8388608}")
    private long maxBytes;

    @Operation(summary = "上传图片，返回访问地址与尺寸")
    @PostMapping("/upload")
    public R<UploadResultVO> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件为空");
        }
        if (file.getSize() > maxBytes) {
            throw new BizException("图片不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }

        String ext = detectImageType(file);
        if (ext == null) {
            log.warn("拒绝非法图片上传: name={} contentType={} size={}",
                    file.getOriginalFilename(), file.getContentType(), file.getSize());
            throw new BizException("仅支持 JPG/PNG/WebP/GIF 图片");
        }
        String declared = file.getContentType();
        if (declared != null && !ALLOWED_TYPES.contains(declared.toLowerCase())) {
            log.warn("图片 Content-Type 与文件头不一致，按文件头处理: declared={} detected={}", declared, ext);
        }

        UploadResultVO result = new UploadResultVO();
        result.setSize(file.getSize());
        result.setContentType("image/" + ("jpg".equals(ext) ? "jpeg" : ext));
        readDimensions(file, result);

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Path.of(uploadDir);
        try {
            Files.createDirectories(dir);
            file.transferTo(new File(dir.toFile(), filename).getAbsoluteFile());
        } catch (Exception e) {
            log.error("图片落盘失败: dir={} filename={} size={}", dir.toAbsolutePath(), filename, file.getSize(), e);
            throw new BizException("图片保存失败，请重试");
        }

        result.setUrl("/uploads/" + filename);
        log.info("图片上传成功: url={} size={} dimensions={}x{}",
                result.getUrl(), result.getSize(), result.getWidth(), result.getHeight());
        return R.ok(result);
    }

    /** 依据文件头判定真实图片类型，返回落盘扩展名；非图片返回 null。 */
    private String detectImageType(MultipartFile file) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (Exception e) {
            log.error("读取上传文件失败: name={}", file.getOriginalFilename(), e);
            return null;
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "png";
        }
        if (read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        if (read >= 6 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F') {
            return "gif";
        }
        return null;
    }

    /** 读取像素尺寸；WebP 等 ImageIO 不支持的格式留空，不阻断上传。 */
    private void readDimensions(MultipartFile file, UploadResultVO result) {
        try (InputStream in = file.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image != null) {
                result.setWidth(image.getWidth());
                result.setHeight(image.getHeight());
            }
        } catch (Exception e) {
            log.debug("无法解析图片尺寸（格式可能不受 ImageIO 支持）: {}", file.getOriginalFilename());
        }
    }
}
