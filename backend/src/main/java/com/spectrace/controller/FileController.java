package com.spectrace.controller;

import com.spectrace.common.BizException;
import com.spectrace.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 文件上传（机型图片、品牌 Logo）。
 * 本地开发写入 ./uploads 目录；生产可替换为对象存储实现。
 */
@Tag(name = "后台-文件上传")
@RestController
@RequestMapping("/api/admin/files")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class FileController {

    @Value("${spectrace.upload-dir:uploads}")
    private String uploadDir;

    @SneakyThrows
    @PostMapping("/upload")
    public R<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException("文件为空");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;

        Path dir = Path.of(uploadDir);
        Files.createDirectories(dir);
        file.transferTo(new File(dir.toFile(), filename).getAbsoluteFile());

        return R.ok("/uploads/" + filename);
    }
}
