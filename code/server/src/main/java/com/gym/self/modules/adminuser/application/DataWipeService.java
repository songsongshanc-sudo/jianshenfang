package com.gym.self.modules.adminuser.application;

import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.gate.GateFaceSync;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * 总账号长按清空。上线前必须删掉入口，见开发文档/正式服务器配置.md。
 */
@Service
public class DataWipeService {

    private static final List<String> TABLES = List.of(
            "lesson_appeal", "lesson_booking", "coach_schedule", "message_audit",
            "visit_pass", "gate_sync_task", "door_log", "door_staff",
            "lesson_ledger", "lesson_account", "groupon_redeem", "groupon_rule",
            "membership_day_adjust", "membership", "payment", "trade_order",
            "user_face", "student_cert", "inbox_message", "complaint_ticket", "repair_ticket", "lost_item",
            "equipment_media", "equipment", "course_pack", "coach",
            "card_product", "store_phone", "store_guide", "store",
            "banner", "notice", "agreement", "service_guide", "franchise_lead", "franchise_page",
            "gate_device", "file_object", "admin_audit", "app_config", "`user`");

    private final JdbcTemplate jdbc;
    private final GateFaceSync gateFaceSync;
    private final TimeProvider timeProvider;
    private final Path publicDir;

    public DataWipeService(JdbcTemplate jdbc, GateFaceSync gateFaceSync, TimeProvider timeProvider,
                           @Value("${gym.files.public-dir}") String publicDir) {
        this.jdbc = jdbc;
        this.gateFaceSync = gateFaceSync;
        this.timeProvider = timeProvider;
        this.publicDir = Path.of(publicDir);
    }

    @Transactional
    public void wipe(AdminPrincipal actor) {
        StoreScope.requireMaster(actor);
        gateFaceSync.removeEveryone();
        silenceForeignKeys();
        for (String table : TABLES) {
            jdbc.update("DELETE FROM " + table);
        }
        restoreForeignKeys();
        jdbc.update("UPDATE member_no_seq SET next_no = 100000001 WHERE id = 1");
        var now = timeProvider.now();
        jdbc.update("INSERT INTO app_config (config_key, config_value, updated_at) VALUES (?,?,?)",
                "entry.debounce.seconds", "15", now);
        jdbc.update("INSERT INTO app_config (config_key, config_value, updated_at) VALUES (?,?,?)",
                "online.window.minutes", "90", now);
        jdbc.update("INSERT INTO app_config (config_key, config_value, updated_at) VALUES (?,?,?)",
                "order.expire.minutes", "15", now);
        jdbc.update("INSERT INTO app_config (config_key, config_value, updated_at) VALUES (?,?,?)",
                "refund.daily.deduct.fen", "", now);
        clearDir(Path.of("data", "private", "face"));
        clearDir(publicDir);
    }

    private void silenceForeignKeys() {
        try {
            jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        } catch (RuntimeException ignored) {
            jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        }
    }

    private void restoreForeignKeys() {
        try {
            jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        } catch (RuntimeException ignored) {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private static void clearDir(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(root))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 文件删不掉也不把已经清空的库再报失败
                        }
                    });
        } catch (IOException ignored) {
            // 同上
        }
    }
}
