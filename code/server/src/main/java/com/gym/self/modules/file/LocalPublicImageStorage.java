package com.gym.self.modules.file;

import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Profile({"local", "test"})
public class LocalPublicImageStorage implements PublicImageStorage {

    private final JdbcTemplate jdbcTemplate;
    private final Snowflake snowflake;
    private final String publicBaseUrl;
    private final Path root = Path.of("data", "public");

    public LocalPublicImageStorage(JdbcTemplate jdbcTemplate, Snowflake snowflake,
                                   @Value("${gym.files.public-base-url}") String publicBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.snowflake = snowflake;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @Override
    public String store(String biz, String contentType, byte[] body) {
        String folder = folder(biz);
        String extension = extension(contentType);
        if (body == null || body.length == 0 || body.length > 5 * 1024 * 1024) {
            throw BizException.badRequest("图片大小需在 5MB 以内");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + extension;
        Path dir = root.resolve(folder);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), body);
        } catch (IOException exception) {
            throw BizException.badRequest("图片保存失败");
        }
        String objectKey = "public/" + folder + "/" + name;
        jdbcTemplate.update(
                "INSERT INTO file_object (id, owner_id, biz, object_key, content_type, created_at) VALUES (?,?,?,?,?,?)",
                snowflake.next(), null, biz, objectKey, contentType, LocalDateTime.now());
        return publicBaseUrl + "/uploads/" + folder + "/" + name;
    }

    private static String folder(String biz) {
        return switch (biz) {
            case "BANNER" -> "banner";
            case "COVER" -> "cover";
            case "GUIDE" -> "guide";
            default -> throw BizException.badRequest("不支持该图片用途");
        };
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> throw BizException.badRequest("只支持 jpg、png、webp、gif");
        };
    }
}
