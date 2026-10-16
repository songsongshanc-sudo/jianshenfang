package com.gym.self.modules.report;

import com.gym.self.common.api.BizException;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> finance(AdminPrincipal actor, LocalDate from, LocalDate to, Long storeId) {
        Range range = normalize(from, to);
        Long scoped = StoreScope.requiredStore(actor, storeId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", range.from().toString());
        out.put("to", range.to().toString());
        out.put("storeId", scoped == null ? null : String.valueOf(scoped));
        out.put("summary", summary(range, scoped));
        out.put("byBizType", byBizType(range, scoped));
        out.put("byCard", byCard(range, scoped));
        out.put("byCoachCourse", byCoachCourse(range, scoped));
        out.put("byStore", actor.master() && scoped == null ? byStore(range) : List.of());
        return out;
    }

    public byte[] financeExcel(AdminPrincipal actor, LocalDate from, LocalDate to, Long storeId) {
        return financeExcel(finance(actor, from, to, storeId), actor.master());
    }

    public byte[] financeExcel(Map<String, Object> data, boolean master) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            writeSummarySheet(wb, data);
            writeNamedSheet(wb, "卡种明细", List.of("卡种ID", "卡种名称", "销量", "金额(元)"),
                    castList(data.get("byCard")),
                    row -> List.of(str(row.get("productId")), str(row.get("name")),
                            str(row.get("count")), yuan(row.get("amountFen"))));
            writeNamedSheet(wb, "教练课程明细", List.of("教练", "课包", "销量", "金额(元)"),
                    castList(data.get("byCoachCourse")),
                    row -> List.of(str(row.get("coachName")), str(row.get("packName")),
                            str(row.get("count")), yuan(row.get("amountFen"))));
            if (master) {
                writeNamedSheet(wb, "分店对比", List.of("门店", "订单数", "金额(元)"),
                        castList(data.get("byStore")),
                        row -> List.of(str(row.get("storeName")), str(row.get("count")), yuan(row.get("amountFen"))));
            }
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception ex) {
            throw BizException.rejected("导出失败，请稍后重试");
        }
    }

    private Map<String, Object> summary(Range range, Long storeId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS order_count,
                       COALESCE(SUM(amount_fen), 0) AS amount_fen,
                       COALESCE(SUM(CASE WHEN biz_type = 'CARD' THEN amount_fen ELSE 0 END), 0) AS card_amount_fen,
                       COALESCE(SUM(CASE WHEN biz_type = 'COURSE' THEN amount_fen ELSE 0 END), 0) AS course_amount_fen,
                       COALESCE(SUM(CASE WHEN biz_type = 'VISIT' THEN amount_fen ELSE 0 END), 0) AS visit_amount_fen,
                       COALESCE(SUM(CASE WHEN biz_type = 'CARD' THEN 1 ELSE 0 END), 0) AS card_count,
                       COALESCE(SUM(CASE WHEN biz_type = 'COURSE' THEN 1 ELSE 0 END), 0) AS course_count,
                       COALESCE(SUM(CASE WHEN biz_type = 'VISIT' THEN 1 ELSE 0 END), 0) AS visit_count
                FROM trade_order
                WHERE status = 'PAID' AND paid_at >= ? AND paid_at < ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(range.startAt());
        args.add(range.endExclusive());
        if (storeId != null) {
            sql.append(" AND store_id = ?");
            args.add(storeId);
        }
        Map<String, Object> row = jdbc.queryForMap(sql.toString(), args.toArray());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderCount", ((Number) row.get("order_count")).longValue());
        m.put("amountFen", ((Number) row.get("amount_fen")).longValue());
        m.put("cardAmountFen", ((Number) row.get("card_amount_fen")).longValue());
        m.put("courseAmountFen", ((Number) row.get("course_amount_fen")).longValue());
        m.put("visitAmountFen", ((Number) row.get("visit_amount_fen")).longValue());
        m.put("cardCount", ((Number) row.get("card_count")).longValue());
        m.put("courseCount", ((Number) row.get("course_count")).longValue());
        m.put("visitCount", ((Number) row.get("visit_count")).longValue());
        return m;
    }

    private List<Map<String, Object>> byBizType(Range range, Long storeId) {
        StringBuilder sql = new StringBuilder("""
                SELECT biz_type, COUNT(*) AS cnt, COALESCE(SUM(amount_fen), 0) AS amount_fen
                FROM trade_order
                WHERE status = 'PAID' AND paid_at >= ? AND paid_at < ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(range.startAt());
        args.add(range.endExclusive());
        if (storeId != null) {
            sql.append(" AND store_id = ?");
            args.add(storeId);
        }
        sql.append(" GROUP BY biz_type ORDER BY amount_fen DESC");
        Map<String, Map<String, Object>> found = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql.toString(), args.toArray())) {
            String type = String.valueOf(row.get("biz_type"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bizType", type);
            item.put("label", bizLabel(type));
            item.put("count", ((Number) row.get("cnt")).longValue());
            item.put("amountFen", ((Number) row.get("amount_fen")).longValue());
            found.put(type, item);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (String type : List.of("CARD", "COURSE", "VISIT")) {
            list.add(found.getOrDefault(type, emptyBiz(type)));
        }
        return list;
    }

    private List<Map<String, Object>> byCard(Range range, Long storeId) {
        StringBuilder sql = new StringBuilder("""
                SELECT product_id, product_name, COUNT(*) AS cnt, COALESCE(SUM(amount_fen), 0) AS amount_fen
                FROM trade_order
                WHERE status = 'PAID' AND biz_type = 'CARD' AND paid_at >= ? AND paid_at < ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(range.startAt());
        args.add(range.endExclusive());
        if (storeId != null) {
            sql.append(" AND store_id = ?");
            args.add(storeId);
        }
        sql.append(" GROUP BY product_id, product_name ORDER BY amount_fen DESC, cnt DESC");
        return jdbc.queryForList(sql.toString(), args.toArray()).stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", String.valueOf(row.get("product_id")));
            m.put("name", row.get("product_name"));
            m.put("count", ((Number) row.get("cnt")).longValue());
            m.put("amountFen", ((Number) row.get("amount_fen")).longValue());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> byCoachCourse(Range range, Long storeId) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.product_id AS pack_id, o.product_name AS pack_name,
                       p.coach_id AS coach_id, COALESCE(c.name, '未关联教练') AS coach_name,
                       COUNT(*) AS cnt, COALESCE(SUM(o.amount_fen), 0) AS amount_fen
                FROM trade_order o
                LEFT JOIN course_pack p ON p.id = o.product_id
                LEFT JOIN coach c ON c.id = p.coach_id
                WHERE o.status = 'PAID' AND o.biz_type = 'COURSE' AND o.paid_at >= ? AND o.paid_at < ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(range.startAt());
        args.add(range.endExclusive());
        if (storeId != null) {
            sql.append(" AND o.store_id = ?");
            args.add(storeId);
        }
        sql.append("""
                GROUP BY o.product_id, o.product_name, p.coach_id, c.name
                ORDER BY amount_fen DESC, cnt DESC
                """);
        return jdbc.queryForList(sql.toString(), args.toArray()).stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("packId", String.valueOf(row.get("pack_id")));
            m.put("packName", row.get("pack_name"));
            Object coachId = row.get("coach_id");
            m.put("coachId", coachId == null ? null : String.valueOf(coachId));
            m.put("coachName", row.get("coach_name"));
            m.put("count", ((Number) row.get("cnt")).longValue());
            m.put("amountFen", ((Number) row.get("amount_fen")).longValue());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> byStore(Range range) {
        String sql = """
                SELECT o.store_id, s.name AS store_name,
                       COUNT(*) AS cnt, COALESCE(SUM(o.amount_fen), 0) AS amount_fen
                FROM trade_order o
                LEFT JOIN store s ON s.id = o.store_id
                WHERE o.status = 'PAID' AND o.paid_at >= ? AND o.paid_at < ?
                GROUP BY o.store_id, s.name
                ORDER BY amount_fen DESC
                """;
        return jdbc.queryForList(sql, range.startAt(), range.endExclusive()).stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            String id = String.valueOf(row.get("store_id"));
            Object name = row.get("store_name");
            m.put("storeId", id);
            m.put("storeName", name == null || String.valueOf(name).isBlank() ? ("门店" + id) : name);
            m.put("count", ((Number) row.get("cnt")).longValue());
            m.put("amountFen", ((Number) row.get("amount_fen")).longValue());
            return m;
        }).toList();
    }

    private static Range normalize(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            throw BizException.badRequest("开始日期不能晚于结束日期");
        }
        if (start.plusYears(2).isBefore(end)) {
            throw BizException.badRequest("查询区间不能超过 2 年");
        }
        return new Range(start, end);
    }

    private static Map<String, Object> emptyBiz(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bizType", type);
        m.put("label", bizLabel(type));
        m.put("count", 0L);
        m.put("amountFen", 0L);
        return m;
    }

    private static String bizLabel(String type) {
        return switch (type) {
            case "CARD" -> "会员卡";
            case "COURSE" -> "教练课程";
            case "VISIT" -> "参观卡";
            default -> type;
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String yuan(Object fen) {
        long v = fen == null ? 0L : ((Number) fen).longValue();
        return BigDecimal.valueOf(v).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }

    private void writeSummarySheet(XSSFWorkbook wb, Map<String, Object> data) {
        Sheet sheet = wb.createSheet("汇总");
        Map<String, Object> summary = castMap(data.get("summary"));
        String[][] rows = {
                {"统计区间", data.get("from") + " ~ " + data.get("to")},
                {"订单数", str(summary.get("orderCount"))},
                {"总收入(元)", yuan(summary.get("amountFen"))},
                {"会员卡收入(元)", yuan(summary.get("cardAmountFen"))},
                {"课程收入(元)", yuan(summary.get("courseAmountFen"))},
                {"参观卡收入(元)", yuan(summary.get("visitAmountFen"))},
                {"会员卡销量", str(summary.get("cardCount"))},
                {"课程销量", str(summary.get("courseCount"))},
                {"参观卡销量", str(summary.get("visitCount"))},
        };
        for (int i = 0; i < rows.length; i++) {
            Row row = sheet.createRow(i);
            row.createCell(0).setCellValue(rows[i][0]);
            row.createCell(1).setCellValue(rows[i][1]);
        }
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 6000);

        Sheet biz = wb.createSheet("收入构成");
        Row head = biz.createRow(0);
        head.createCell(0).setCellValue("类型");
        head.createCell(1).setCellValue("销量");
        head.createCell(2).setCellValue("金额(元)");
        int r = 1;
        for (Map<String, Object> item : castList(data.get("byBizType"))) {
            Row row = biz.createRow(r++);
            row.createCell(0).setCellValue(str(item.get("label")));
            row.createCell(1).setCellValue(str(item.get("count")));
            row.createCell(2).setCellValue(yuan(item.get("amountFen")));
        }
    }

    private interface RowMapper {
        List<String> cells(Map<String, Object> row);
    }

    private void writeNamedSheet(XSSFWorkbook wb, String name, List<String> headers,
                                 List<Map<String, Object>> rows, RowMapper mapper) {
        Sheet sheet = wb.createSheet(name);
        Row head = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            head.createCell(i).setCellValue(headers.get(i));
        }
        int r = 1;
        for (Map<String, Object> item : rows) {
            Row row = sheet.createRow(r++);
            List<String> cells = mapper.cells(item);
            for (int i = 0; i < cells.size(); i++) {
                row.createCell(i).setCellValue(cells.get(i));
            }
        }
        for (int i = 0; i < headers.size(); i++) {
            sheet.setColumnWidth(i, 4500);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private record Range(LocalDate from, LocalDate to) {
        LocalDateTime startAt() {
            return LocalDateTime.of(from, LocalTime.MIN);
        }

        LocalDateTime endExclusive() {
            return LocalDateTime.of(to.plusDays(1), LocalTime.MIN);
        }
    }
}
