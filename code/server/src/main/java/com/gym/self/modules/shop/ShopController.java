package com.gym.self.modules.shop;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import com.gym.self.modules.order.application.OrderService;
import com.gym.self.modules.user.auth.CurrentMp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @PostMapping("/api/mp/repairs")
    public ApiResponse<IdView> repair(@Valid @RequestBody RepairRequest request) {
        return ApiResponse.ok(new IdView(shopService.repair(CurrentMp.formal().userId(), parse(request.storeId()),
                request.equipmentCode(), request.content(), request.imageUrl())));
    }

    @GetMapping("/api/mp/repairs")
    public ApiResponse<List<Map<String, Object>>> myRepairs() {
        return ApiResponse.ok(shopService.myRepairs(CurrentMp.formal().userId()));
    }

    @PostMapping("/api/mp/complaints")
    public ApiResponse<IdView> complaint(@Valid @RequestBody ComplaintRequest request) {
        return ApiResponse.ok(new IdView(shopService.complaint(CurrentMp.formal().userId(), parse(request.storeId()),
                request.category(), request.content(), request.imageUrl())));
    }

    @GetMapping("/api/mp/complaints")
    public ApiResponse<List<Map<String, Object>>> myComplaints() {
        return ApiResponse.ok(shopService.myComplaints(CurrentMp.formal().userId()));
    }

    @PostMapping("/api/mp/lost")
    public ApiResponse<IdView> lost(@Valid @RequestBody LostRequest request) {
        return ApiResponse.ok(new IdView(shopService.lost(CurrentMp.formal().userId(), parse(request.storeId()),
                request.kind(), request.name(), request.content(), request.imageUrl(), request.visible())));
    }

    @GetMapping("/api/mp/lost")
    public ApiResponse<List<Map<String, Object>>> lostFeed(@RequestParam String storeId, @RequestParam(defaultValue = "ALL") String tab) {
        return ApiResponse.ok(shopService.lostFeed(CurrentMp.formal().userId(), parse(storeId), tab));
    }

    @GetMapping("/api/mp/equipment")
    public ApiResponse<List<Map<String, Object>>> equipment(@RequestParam String storeId) {
        return ApiResponse.ok(shopService.equipmentOf(parse(storeId)));
    }

    @GetMapping("/api/mp/equipment/by-code")
    public ApiResponse<Map<String, Object>> equipmentCode(@RequestParam String storeId, @RequestParam String code) {
        return ApiResponse.ok(shopService.equipmentByCode(parse(storeId), code));
    }

    @PostMapping("/api/mp/groupon/redeem")
    public ApiResponse<IdView> redeem(@Valid @RequestBody GrouponRequest request) {
        return ApiResponse.ok(new IdView(shopService.redeemGroupon(CurrentMp.formal().userId(), parse(request.storeId()),
                request.platform(), request.code())));
    }

    @GetMapping("/api/mp/reviews")
    public ApiResponse<List<Map<String, Object>>> reviews(@RequestParam String platform) {
        CurrentMp.formal();
        return ApiResponse.ok(List.of());
    }

    @GetMapping("/api/mp/student")
    public ApiResponse<StatusView> student() {
        return ApiResponse.ok(new StatusView(shopService.studentStatus(CurrentMp.formal().userId())));
    }

    @GetMapping("/api/mp/coaches")
    public ApiResponse<List<Map<String, Object>>> coaches(@RequestParam String storeId) {
        return ApiResponse.ok(shopService.coaches(parse(storeId)));
    }

    @GetMapping("/api/mp/packs")
    public ApiResponse<List<Map<String, Object>>> packs(@RequestParam String storeId) {
        return ApiResponse.ok(shopService.packs(parse(storeId)));
    }

    @PostMapping("/api/mp/packs/{id}/orders")
    public ApiResponse<OrderService.Created> buy(@PathVariable String id, @Valid @RequestBody BuyRequest request,
                                                 @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.ok(shopService.buyPack(CurrentMp.formal().userId(), parse(id), parse(request.agreementId()),
                request.agreementVersion(), idempotencyKey));
    }

    @GetMapping("/api/mp/lessons")
    public ApiResponse<List<Map<String, Object>>> lessons() {
        return ApiResponse.ok(shopService.myLessons(CurrentMp.formal().userId()));
    }

    @PostMapping("/api/mp/lessons/qr")
    public ApiResponse<TokenView> qr(@Valid @RequestBody PackRequest request) {
        return ApiResponse.ok(new TokenView(shopService.lessonQr(CurrentMp.formal().userId(), parse(request.packId()))));
    }

    @PostMapping("/api/mp/lessons/redeem")
    public ApiResponse<Void> redeemLesson(@Valid @RequestBody TokenRequest request) {
        shopService.redeemLesson(CurrentMp.formal().userId(), request.token());
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/mp/franchise")
    public ApiResponse<Map<String, Object>> franchise() {
        return ApiResponse.ok(shopService.franchisePage());
    }

    @PostMapping("/api/mp/franchise/leads")
    public ApiResponse<IdView> lead(@Valid @RequestBody LeadRequest request) {
        return ApiResponse.ok(new IdView(shopService.franchiseLead(request.name(), request.phone(), request.budget(),
                request.province(), request.city())));
    }

    @GetMapping("/api/mp/messages")
    public ApiResponse<List<Map<String, Object>>> messages() {
        return ApiResponse.ok(shopService.messages(CurrentMp.formal().userId()));
    }

    @PostMapping("/api/mp/messages/{id}/read")
    public ApiResponse<Void> read(@PathVariable String id) {
        shopService.readMessage(CurrentMp.formal().userId(), parse(id));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/repairs")
    public ApiResponse<List<Map<String, Object>>> adminRepairs(@RequestParam(required = false) String storeId) {
        return ApiResponse.ok(shopService.adminRepairs(CurrentAdmin.get(), optional(storeId)));
    }

    @PostMapping("/api/admin/repairs/{id}/status")
    public ApiResponse<Void> repairStatus(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        shopService.updateTicket(CurrentAdmin.get(), "repair_ticket", parse(id), request.status());
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/complaints")
    public ApiResponse<List<Map<String, Object>>> adminComplaints(@RequestParam(required = false) String storeId) {
        return ApiResponse.ok(shopService.adminComplaints(CurrentAdmin.get(), optional(storeId)));
    }

    @PostMapping("/api/admin/complaints/{id}/status")
    public ApiResponse<Void> complaintStatus(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        shopService.updateTicket(CurrentAdmin.get(), "complaint_ticket", parse(id), request.status());
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/lost")
    public ApiResponse<List<Map<String, Object>>> adminLost(@RequestParam(required = false) String storeId) {
        return ApiResponse.ok(shopService.adminLost(CurrentAdmin.get(), optional(storeId)));
    }

    @PostMapping("/api/admin/lost/{id}/status")
    public ApiResponse<Void> lostStatus(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        shopService.updateTicket(CurrentAdmin.get(), "lost_item", parse(id), request.status());
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/equipment")
    public ApiResponse<IdView> equipmentSave(@Valid @RequestBody EquipmentRequest request) {
        return ApiResponse.ok(new IdView(shopService.saveEquipment(CurrentAdmin.get(),
                request.id() == null || request.id().isBlank() ? null : parse(request.id()),
                parse(request.storeId()), request.code(), request.name(), request.intro(), request.imageUrl(), request.videoUrl())));
    }

    @GetMapping("/api/admin/equipment")
    public ApiResponse<List<Map<String, Object>>> adminEquipment(@RequestParam String storeId) {
        StoreScopeCheck.masterOrOwn(CurrentAdmin.get(), parse(storeId));
        List<Map<String, Object>> rows = shopService.equipmentOf(parse(storeId));
        rows.forEach(row -> row.put("id", String.valueOf(row.get("id"))));
        return ApiResponse.ok(rows);
    }

    @DeleteMapping("/api/admin/equipment/{id}")
    public ApiResponse<Void> deleteEquipment(@PathVariable String id) {
        shopService.deleteEquipment(CurrentAdmin.get(), parse(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/groupon-rules")
    public ApiResponse<Void> groupon(@Valid @RequestBody GrouponRuleRequest request) {
        shopService.addGrouponRule(CurrentAdmin.get(), parse(request.storeId()), request.platform(), request.code(), parse(request.cardProductId()));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/groupon-rules")
    public ApiResponse<List<Map<String, Object>>> grouponRules(@RequestParam(required = false) String storeId) {
        List<Map<String, Object>> rows = shopService.grouponRules(CurrentAdmin.get(), optional(storeId));
        rows.forEach(row -> {
            row.put("id", String.valueOf(row.get("id")));
            row.put("store_id", String.valueOf(row.get("store_id")));
            row.put("card_product_id", String.valueOf(row.get("card_product_id")));
        });
        return ApiResponse.ok(rows);
    }

    @PutMapping("/api/admin/groupon-rules/{id}")
    public ApiResponse<Void> updateGroupon(@PathVariable String id, @Valid @RequestBody GrouponUpdateRequest request) {
        shopService.updateGrouponRule(CurrentAdmin.get(), parse(id), parse(request.storeId()), request.platform(), parse(request.cardProductId()));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/groupon-rules/{id}")
    public ApiResponse<Void> deleteGroupon(@PathVariable String id) {
        shopService.deleteGrouponRule(CurrentAdmin.get(), parse(id));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/coaches")
    public ApiResponse<List<Map<String, Object>>> adminCoaches(@RequestParam String storeId) {
        StoreScopeCheck.masterOrOwn(CurrentAdmin.get(), parse(storeId));
        List<Map<String, Object>> rows = shopService.coaches(parse(storeId));
        rows.forEach(row -> row.put("id", String.valueOf(row.get("id"))));
        return ApiResponse.ok(rows);
    }

    @PutMapping("/api/admin/coaches/{id}")
    public ApiResponse<Void> updateCoach(@PathVariable String id, @Valid @RequestBody CoachUpdate request) {
        shopService.updateCoach(CurrentAdmin.get(), parse(id), request.name(), request.phone(), request.intro());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/coaches/{id}")
    public ApiResponse<Void> deleteCoach(@PathVariable String id) {
        shopService.deleteCoach(CurrentAdmin.get(), parse(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/coaches")
    public ApiResponse<IdView> coach(@Valid @RequestBody CoachRequest request) {
        return ApiResponse.ok(new IdView(shopService.saveCoach(CurrentAdmin.get(), parse(request.storeId()), request.name(), request.phone(), request.intro())));
    }

    @PostMapping("/api/admin/packs")
    public ApiResponse<IdView> pack(@Valid @RequestBody PackSave request) {
        return ApiResponse.ok(new IdView(shopService.savePack(CurrentAdmin.get(), parse(request.coachId()), request.name(),
                request.priceFen(), request.lessonCount(), request.content(), request.audience())));
    }

    @GetMapping("/api/admin/franchise")
    public ApiResponse<Map<String, Object>> adminFranchise() {
        return ApiResponse.ok(shopService.franchisePage());
    }

    @PostMapping("/api/admin/franchise")
    public ApiResponse<Void> saveFranchise(@RequestBody FranchiseRequest request) {
        shopService.saveFranchise(CurrentAdmin.get(), request.intro(), request.hotline(), request.points(), request.support(), request.steps());
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/franchise/leads")
    public ApiResponse<List<Map<String, Object>>> leads() {
        List<Map<String, Object>> rows = shopService.leads(CurrentAdmin.get());
        rows.forEach(row -> row.put("id", String.valueOf(row.get("id"))));
        return ApiResponse.ok(rows);
    }

    @PutMapping("/api/admin/franchise/leads/{id}")
    public ApiResponse<Void> updateLead(@PathVariable String id, @Valid @RequestBody LeadRequest request) {
        shopService.updateLead(CurrentAdmin.get(), parse(id), request.name(), request.phone(), request.budget(), request.province(), request.city());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/franchise/leads/{id}")
    public ApiResponse<Void> deleteLead(@PathVariable String id) {
        shopService.deleteLead(CurrentAdmin.get(), parse(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/messages")
    public ApiResponse<Void> message(@Valid @RequestBody MessageRequest request) {
        shopService.sendMessage(CurrentAdmin.get(), parse(request.userId()), request.title(), request.content());
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/violations")
    public ApiResponse<Void> violation(@Valid @RequestBody ViolationRequest request) {
        shopService.violation(CurrentAdmin.get(), parse(request.membershipId()), request.deltaDays(), request.revoke(), request.reason());
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/users/{id}/face")
    public ApiResponse<Map<String, Object>> face(@PathVariable String id) {
        return ApiResponse.ok(shopService.faceRecord(CurrentAdmin.get(), parse(id)));
    }

    private static long parse(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw BizException.badRequest("参数不正确");
        }
    }

    private static Long optional(String id) {
        return id == null || id.isBlank() ? null : parse(id);
    }

    public record RepairRequest(@NotBlank String storeId, String equipmentCode, @NotBlank String content, String imageUrl) {
    }

    public record ComplaintRequest(@NotBlank String storeId, @NotBlank String category, @NotBlank String content, String imageUrl) {
    }

    public record LostRequest(@NotBlank String storeId, @NotBlank String kind, @NotBlank String name, @NotBlank String content,
                              String imageUrl, boolean visible) {
    }

    public record GrouponRequest(@NotBlank String storeId, @NotBlank String platform, @NotBlank String code) {
    }

    public record BuyRequest(@NotBlank String agreementId, int agreementVersion) {
    }

    public record PackRequest(@NotBlank String packId) {
    }

    public record TokenRequest(@NotBlank String token) {
    }

    public record LeadRequest(@NotBlank String name, @NotBlank String phone, String budget, @NotBlank String province, @NotBlank String city) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record EquipmentRequest(String id, @NotBlank String storeId, @NotBlank String code, @NotBlank String name, String intro, String imageUrl, String videoUrl) {
    }

    public record GrouponRuleRequest(@NotBlank String storeId, @NotBlank String platform, @NotBlank String code, @NotBlank String cardProductId) {
    }

    public record GrouponUpdateRequest(@NotBlank String storeId, @NotBlank String platform, @NotBlank String cardProductId) {
    }

    public record CoachUpdate(@NotBlank String name, @NotBlank String phone, String intro) {
    }

    public record CoachRequest(@NotBlank String storeId, @NotBlank String name, @NotBlank String phone, String intro) {
    }

    public record PackSave(@NotBlank String coachId, @NotBlank String name, long priceFen, int lessonCount, String content, String audience) {
    }

    public record FranchiseRequest(String intro, String hotline, String points, String support, String steps) {
    }

    public record MessageRequest(@NotBlank String userId, @NotBlank String title, @NotBlank String content) {
    }

    public record ViolationRequest(@NotBlank String membershipId, int deltaDays, boolean revoke, @NotBlank String reason) {
    }

    public record IdView(String id) {
    }

    public record StatusView(String status) {
    }

    public record TokenView(String token) {
    }
}
