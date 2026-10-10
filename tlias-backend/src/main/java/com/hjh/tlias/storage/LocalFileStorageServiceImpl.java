package com.hjh.tlias.storage;

import com.hjh.tlias.config.properties.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 文件存储的本地磁盘实现：文件写进项目目录下的 uploads/。
 *
 * 为什么本地开发够用：开发阶段没有对象存储也能把「上传 → 存 → 按 URL 读回」
 * 这条链路完整跑通，前端对接完全不受影响。上了生产环境再切 OSS 实现。
 *
 * 存法：uploads/yyyy/MM/dd/uuid.ext。
 *   - 按日期分目录：单目录放几万个文件后，操作系统列目录都会变慢
 *   - uuid 做文件名：用户传的原始文件名可能撞车、可能带奇怪字符（甚至路径穿越），
 *     干脆全部弃用，只保留扩展名。同名上传一百次也不会互相覆盖
 *
 * @Profile("!oss")：只要没激活 oss profile 就用本地实现（和 CacheService 的
 * 「!redis」是一个思路）。
 */
@Slf4j
@Service
@Profile("!oss")
@RequiredArgsConstructor
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final StorageProperties storageProperties;

    @Override
    public String upload(byte[] content, String originalFilename) {
        String key = buildKey(originalFilename);
        Path target = Path.of(storageProperties.getLocal().getDir()).resolve(key);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            // 磁盘写不动属于系统故障（满盘、没权限），不是业务问题，
            // 包成非受检异常交给全局处理器记全栈日志。业务侧看到的会是「系统繁忙」
            throw new UncheckedIOException("文件落盘失败: " + target, e);
        }

        // URL 里的 /uploads/ 对应 WebMvcConfig 里注册的静态资源映射，
        // base-url 则带着 context-path（http://localhost:8080/api）
        String url = storageProperties.getLocal().getBaseUrl() + "/uploads/" + key;
        log.debug("文件已落本地磁盘: {} -> {}", target, url);
        return url;
    }

    /**
     * 生成存储路径：日期目录 + 随机文件名 + 原扩展名。
     * 扩展名转小写，免得 photo.JPG 和 photo.jpg 在 URL 里看起来像两种东西。
     */
    private String buildKey(String originalFilename) {
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        return LocalDate.now().format(DATE_DIR) + "/" + UUID.randomUUID() + ext;
    }

}
