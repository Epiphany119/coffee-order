package com.coffee.module.menu.biz.infra.storage;

import com.coffee.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地图片存储：文件落盘到 {user.home}/coffee-uploads/{storeId}/，对外访问路径 /uploads/{storeId}/{文件名}
 *
 * 存储根目录可后续改为配置项，仅此一处集中管理。
 */
@Component
public class LocalImageStorage {

    /** 图片扩展名白名单 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    /** 单文件大小上限 5MB */
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private static final String ROOT_NAME = "coffee-uploads";

    private Path root() {
        return Paths.get(System.getProperty("user.home"), ROOT_NAME);
    }

    /**
     * 保存上传图片，返回可访问的 URL 路径（如 /uploads/5/xxxx.png）
     */
    public String store(Long storeId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(400, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ServiceException(400, "图片不能超过 5MB");
        }
        String ext = extOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new ServiceException(400, "仅支持图片格式: jpg/png/gif/webp");
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = root().resolve(String.valueOf(storeId));
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ServiceException(500, "图片保存失败: " + e.getMessage());
        }
        return "/uploads/" + storeId + "/" + filename;
    }

    private String extOf(String originalName) {
        if (originalName == null) return "";
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) return "";
        return originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
