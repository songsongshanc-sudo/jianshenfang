package com.gym.self.modules.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.model.ObjectMetadata;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 正式环境公开图/视频走阿里云 OSS。以后若换腾讯云 COS，新增实现类并改 {@code prod} 注入即可，
 * 业务仍只依赖 {@link PublicImageStorage}。
 */
@Component
@Profile("prod")
public class OssPublicImageStorage implements PublicImageStorage {

    private static final Logger log = LoggerFactory.getLogger(OssPublicImageStorage.class);

    private final JdbcTemplate jdbcTemplate;
    private final Snowflake snowflake;
    private final String endpoint;
    private final String bucket;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String publicBaseUrl;

    private volatile OSS client;

    public OssPublicImageStorage(
            JdbcTemplate jdbcTemplate,
            Snowflake snowflake,
            @Value("${gym.files.oss.endpoint:}") String endpoint,
            @Value("${gym.files.oss.bucket:}") String bucket,
            @Value("${gym.files.oss.access-key-id:}") String accessKeyId,
            @Value("${gym.files.oss.access-key-secret:}") String accessKeySecret,
            @Value("${gym.files.oss.public-base-url:}") String publicBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.snowflake = snowflake;
        this.endpoint = trim(endpoint);
        this.bucket = trim(bucket);
        this.accessKeyId = trim(accessKeyId);
        this.accessKeySecret = trim(accessKeySecret);
        this.publicBaseUrl = trimTrailingSlash(trim(publicBaseUrl));
    }

    @Override
    public String store(String biz, String contentType, byte[] body) {
        ensureConfigured();
        PublicObjectNaming.validate(biz, contentType, body);
        String folder = PublicObjectNaming.folder(biz);
        String extension = PublicObjectNaming.extension(biz, contentType);
        String name = UUID.randomUUID().toString().replace("-", "") + extension;
        String objectKey = "public/" + folder + "/" + name;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(body.length);
        metadata.setContentType(contentType);
        try {
            client().putObject(bucket, objectKey, new ByteArrayInputStream(body), metadata);
        } catch (OSSException | ClientException exception) {
            log.error("oss upload failed: {}", objectKey, exception);
            throw BizException.rejected("图片上传失败，请稍后重试");
        }
        jdbcTemplate.update(
                "INSERT INTO file_object (id, owner_id, biz, object_key, content_type, created_at) VALUES (?,?,?,?,?,?)",
                snowflake.next(), null, biz, objectKey, contentType, LocalDateTime.now());
        return publicBaseUrl + "/" + objectKey;
    }

    @PreDestroy
    void shutdown() {
        OSS current = client;
        if (current != null) {
            current.shutdown();
        }
    }

    private void ensureConfigured() {
        if (endpoint.isEmpty() || bucket.isEmpty() || accessKeyId.isEmpty()
                || accessKeySecret.isEmpty() || publicBaseUrl.isEmpty()) {
            throw BizException.badRequest("对象存储尚未配置");
        }
    }

    private OSS client() {
        OSS current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            }
            return client;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
