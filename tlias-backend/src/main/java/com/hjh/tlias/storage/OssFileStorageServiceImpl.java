package com.hjh.tlias.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.hjh.tlias.config.properties.AliyunOssProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 文件存储的阿里云 OSS 实现：文件传到 bucket，返回公网 URL。
 *
 * 只在激活 oss profile 时装配。凭证（access-key 等）在 application-local.yml 的
 * aliyun.oss 那一组里，永远不进 git。
 *
 * 和本地实现的差异只有「文件去哪」：
 *   - 存法一样按 yyyy/MM/dd/uuid.ext 组织（OSS 里叫 object key）
 *   - 上传后把对象 ACL 设成 public-read，浏览器才能直接加载这个 URL 显示头像
 *     （bucket 如果开了「阻止公共访问」，这里会报 AccessDenied，要去控制台关掉）
 */
@Slf4j
@Service
@Profile("oss")
@RequiredArgsConstructor
public class OssFileStorageServiceImpl implements FileStorageService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final OSS ossClient;
    private final AliyunOssProperties ossProperties;

    @Override
    public String upload(byte[] content, String originalFilename) {
        String key = buildKey(originalFilename);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentTypeOf(key));
        metadata.setObjectAcl(CannedAccessControlList.PublicRead);

        ossClient.putObject(ossProperties.getBucketName(), key,
                new ByteArrayInputStream(content), metadata);

        String url = "https://" + ossProperties.getBucketName() + "."
                + cleanEndpoint() + "/" + key;
        log.debug("文件已上传 OSS: {} -> {}", key, url);
        return url;
    }

    private String buildKey(String originalFilename) {
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        return LocalDate.now().format(DATE_DIR) + "/" + UUID.randomUUID() + ext;
    }

    /**
     * endpoint 按官方文档填的是「不带协议」的域名，比如 oss-cn-chengdu.aliyuncs.com。
     * 但手滑带上 https:// 前缀是常见情况，这里统一剥掉，
     * 免得拼出 https://bucket.https://oss-... 这种哭笑不得的 URL。
     */
    private String cleanEndpoint() {
        return ossProperties.getEndpoint().replaceFirst("^https?://", "");
    }

    /**
     * 告诉 OSS 这个对象的 MIME 类型。不设的话默认 application/octet-stream，
     * 浏览器访问 URL 会变成「下载文件」而不是直接显示图片。
     */
    private String contentTypeOf(String key) {
        String ext = key.substring(key.lastIndexOf('.') + 1);
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg"; // jpg / jpeg
        };
    }

}
