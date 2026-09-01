package com.coffee.web.storage;

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
 * 三端个人资料图片的本地存储。
 * <p>目录：{user.home}/coffee-uploads/profile/{scope}/{ownerId}/，对外仍通过 /uploads/** 访问。</p>
 */
@Component
public class LocalProfileImageStorage {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private static final String ROOT_NAME = "coffee-uploads";

    public String store(String scope, Long ownerId, MultipartFile file) {
        if (scope == null || !scope.matches("[a-z0-9_-]{1,32}")) {
            throw new ServiceException(400, "图片归属无效");
        }
        if (ownerId == null || ownerId <= 0) {
            throw new ServiceException(400, "图片归属账号无效");
        }
        if (file == null || file.isEmpty()) {
            throw new ServiceException(400, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ServiceException(400, "图片不能超过 5MB");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ServiceException(400, "仅支持 jpg/png/webp 图片");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path directory = Paths.get(System.getProperty("user.home"), ROOT_NAME, "profile", scope,
                String.valueOf(ownerId));
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, directory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ServiceException(500, "图片保存失败: " + e.getMessage());
        }
        return "/uploads/profile/" + scope + "/" + ownerId + "/" + filename;
    }

    private String extensionOf(String originalName) {
        if (originalName == null) return "";
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) return "";
        return originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
