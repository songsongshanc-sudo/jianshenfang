package com.gym.self.modules.user.application;

import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FaceFileService {

    private final JdbcTemplate jdbcTemplate;
    private final Snowflake snowflake;

    public FaceFileService(JdbcTemplate jdbcTemplate, Snowflake snowflake) {
        this.jdbcTemplate = jdbcTemplate;
        this.snowflake = snowflake;
    }

    public String presign(long userId, String contentType) {
        if (contentType == null || !contentType.startsWith("image/")) {
            throw BizException.badRequest("请上传图片");
        }
        String objectKey = "face/" + userId + "/" + UUID.randomUUID().toString().replace("-", "") + ".jpg";
        jdbcTemplate.update(
                "INSERT INTO file_object (id, owner_id, biz, object_key, content_type, created_at) VALUES (?,?,?,?,?,?)",
                snowflake.next(), userId, "FACE", objectKey, contentType, LocalDateTime.now());
        return objectKey;
    }

    public void save(long userId, String objectKey, byte[] body) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_object WHERE owner_id = ? AND object_key = ? AND biz = 'FACE'",
                Integer.class, userId, objectKey);
        if (count == null || count == 0) {
            throw BizException.badRequest("上传凭证不正确");
        }
        if (body == null || body.length == 0 || body.length > 5 * 1024 * 1024) {
            throw BizException.badRequest("照片大小不正确");
        }
        try {
            Path file = path(userId, objectKey);
            Files.createDirectories(file.getParent());
            Files.write(file, body);
        } catch (IOException exception) {
            throw BizException.badRequest("照片保存失败");
        }
        jdbcTemplate.update("UPDATE file_object SET content_type = ? WHERE object_key = ?", "uploaded:" + body.length, objectKey);
    }

    public byte[] read(long userId, String objectKey) {
        size(userId, objectKey);
        try {
            Path file = path(userId, objectKey);
            if (!Files.isRegularFile(file)) {
                throw BizException.badRequest("没有照片");
            }
            return Files.readAllBytes(file);
        } catch (BizException exception) {
            throw exception;
        } catch (IOException exception) {
            throw BizException.badRequest("没有照片");
        }
    }

    private static Path path(long userId, String objectKey) {
        String prefix = "face/" + userId + "/";
        if (objectKey == null || !objectKey.startsWith(prefix) || objectKey.contains("..")) {
            throw BizException.badRequest("上传凭证不正确");
        }
        Path root = Path.of("data", "private", "face").toAbsolutePath().normalize();
        Path file = root.resolve(objectKey.substring("face/".length())).normalize();
        if (!file.startsWith(root)) {
            throw BizException.badRequest("上传凭证不正确");
        }
        return file;
    }

    public long size(long userId, String objectKey) {
        String marker = jdbcTemplate.query(
                "SELECT content_type FROM file_object WHERE owner_id = ? AND object_key = ? AND biz = 'FACE'",
                rs -> rs.next() ? rs.getString(1) : null, userId, objectKey);
        if (marker == null || !marker.startsWith("uploaded:")) {
            throw BizException.badRequest("请先上传照片");
        }
        return Long.parseLong(marker.substring("uploaded:".length()));
    }
}
