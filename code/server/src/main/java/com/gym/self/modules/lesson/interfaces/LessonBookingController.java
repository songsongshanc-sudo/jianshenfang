package com.gym.self.modules.lesson.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import com.gym.self.modules.lesson.application.LessonBookingService;
import com.gym.self.modules.user.auth.CurrentMp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
public class LessonBookingController {

    private final LessonBookingService lessonBookingService;

    public LessonBookingController(LessonBookingService lessonBookingService) {
        this.lessonBookingService = lessonBookingService;
    }

    @GetMapping("/api/admin/coaches/{id}/schedules")
    public ApiResponse<List<Map<String, Object>>> schedules(@PathVariable String id) {
        return ApiResponse.ok(lessonBookingService.schedules(parse(id)));
    }

    @PutMapping("/api/admin/coaches/{id}/schedules")
    public ApiResponse<Void> replaceSchedules(@PathVariable String id, @Valid @RequestBody ScheduleRequest request) {
        lessonBookingService.replaceSchedules(CurrentAdmin.get(), parse(id), request.startHms());
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/message-audits")
    public ApiResponse<List<Map<String, Object>>> audits(@RequestParam(required = false) String storeId,
                                                         @RequestParam(required = false) String coachId,
                                                         @RequestParam(required = false) String bizType) {
        return ApiResponse.ok(lessonBookingService.adminAudits(CurrentAdmin.get(),
                storeId == null || storeId.isBlank() ? null : parse(storeId),
                coachId == null || coachId.isBlank() ? null : parse(coachId),
                bizType));
    }

    @GetMapping("/api/admin/lesson-bookings/{id}")
    public ApiResponse<Map<String, Object>> adminBooking(@PathVariable String id) {
        return ApiResponse.ok(lessonBookingService.adminBookingDetail(CurrentAdmin.get(), parse(id)));
    }

    @PostMapping("/api/admin/lesson-bookings/{id}/approve")
    public ApiResponse<Map<String, Object>> adminApprove(@PathVariable String id) {
        return ApiResponse.ok(lessonBookingService.adminApprove(CurrentAdmin.get(), parse(id)));
    }

    @PostMapping("/api/admin/lesson-bookings/{id}/reject")
    public ApiResponse<Map<String, Object>> adminReject(@PathVariable String id, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(lessonBookingService.adminReject(CurrentAdmin.get(), parse(id), request.reason()));
    }

    @PostMapping("/api/admin/lesson-bookings/{id}/refund")
    public ApiResponse<Map<String, Object>> adminRefund(@PathVariable String id) {
        return ApiResponse.ok(lessonBookingService.adminRefund(CurrentAdmin.get(), parse(id)));
    }

    @PostMapping("/api/admin/lesson-bookings/{id}/reject-appeal")
    public ApiResponse<Map<String, Object>> adminRejectAppeal(@PathVariable String id, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(lessonBookingService.adminRejectAppeal(CurrentAdmin.get(), parse(id), request.reason()));
    }

    @GetMapping("/api/mp/packs/{id}/booking-offer")
    public ApiResponse<Map<String, Object>> offer(@PathVariable String id) {
        return ApiResponse.ok(lessonBookingService.bookingOffer(CurrentMp.formal().userId(), parse(id)));
    }

    @GetMapping("/api/mp/packs/{id}/occupied-seats")
    public ApiResponse<List<String>> seats(@PathVariable String id,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @RequestParam String startHm) {
        return ApiResponse.ok(lessonBookingService.occupiedSeats(parse(id), date, startHm));
    }

    @PostMapping("/api/mp/lesson-bookings")
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateBooking request) {
        return ApiResponse.ok(lessonBookingService.createBooking(CurrentMp.formal().userId(), parse(request.packId()),
                request.classDate(), request.startHm(), request.seatRow(), request.seatCol()));
    }

    @GetMapping("/api/mp/lesson-bookings")
    public ApiResponse<List<Map<String, Object>>> myBookings() {
        return ApiResponse.ok(lessonBookingService.myBookings(CurrentMp.formal().userId()));
    }

    @GetMapping("/api/mp/lesson-bookings/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        return ApiResponse.ok(lessonBookingService.bookingDetail(CurrentMp.formal().userId(), parse(id), false));
    }

    @PostMapping("/api/mp/lesson-bookings/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable String id) {
        return ApiResponse.ok(lessonBookingService.cancelByUser(CurrentMp.formal().userId(), parse(id)));
    }

    @PostMapping("/api/mp/lesson-bookings/{id}/appeals")
    public ApiResponse<Map<String, Object>> appeal(@PathVariable String id, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(lessonBookingService.createAppeal(CurrentMp.formal().userId(), parse(id), request.reason()));
    }

    @GetMapping("/api/mp/coach/lesson-bookings")
    public ApiResponse<List<Map<String, Object>>> coachList() {
        return ApiResponse.ok(lessonBookingService.coachBookings(CurrentMp.formal().userId()));
    }

    @GetMapping("/api/mp/coach/lesson-bookings/{id}")
    public ApiResponse<Map<String, Object>> coachDetail(@PathVariable String id) {
        return ApiResponse.ok(lessonBookingService.bookingDetail(CurrentMp.formal().userId(), parse(id), true));
    }

    @PostMapping("/api/mp/coach/lesson-bookings/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable String id) {
        return ApiResponse.ok(lessonBookingService.coachApprove(CurrentMp.formal().userId(), parse(id)));
    }

    @PostMapping("/api/mp/coach/lesson-bookings/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable String id, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(lessonBookingService.coachReject(CurrentMp.formal().userId(), parse(id), request.reason()));
    }

    @PostMapping("/api/mp/coach/lesson-bookings/{id}/refund")
    public ApiResponse<Map<String, Object>> refund(@PathVariable String id) {
        return ApiResponse.ok(lessonBookingService.coachRefund(CurrentMp.formal().userId(), parse(id)));
    }

    @PostMapping("/api/mp/coach/lesson-bookings/{id}/reject-appeal")
    public ApiResponse<Map<String, Object>> rejectAppeal(@PathVariable String id, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(lessonBookingService.coachRejectAppeal(CurrentMp.formal().userId(), parse(id), request.reason()));
    }

    private static long parse(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw BizException.badRequest("参数不正确");
        }
    }

    public record ScheduleRequest(List<String> startHms) {
    }

    public record CreateBooking(@NotBlank String packId,
                                @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate classDate,
                                @NotBlank String startHm,
                                @NotNull Integer seatRow,
                                @NotNull Integer seatCol) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }
}
