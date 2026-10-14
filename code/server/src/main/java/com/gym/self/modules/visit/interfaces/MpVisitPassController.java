package com.gym.self.modules.visit.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.order.application.OrderService;
import com.gym.self.modules.user.auth.CurrentMp;
import com.gym.self.modules.visit.application.VisitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mp/visit")
public class MpVisitPassController {

    private final VisitService visitService;
    private final OrderService orderService;

    public MpVisitPassController(VisitService visitService, OrderService orderService) {
        this.visitService = visitService;
        this.orderService = orderService;
    }

    @GetMapping("/offer")
    public ApiResponse<VisitService.OfferView> offer(@RequestParam(required = false) String storeId) {
        Long scoped = storeId == null || storeId.isBlank() ? null : parseId(storeId);
        return ApiResponse.ok(visitService.offer(CurrentMp.formal().userId(), scoped));
    }

    @PostMapping("/orders")
    public ApiResponse<VisitService.Created> create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                    @Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(visitService.createOrder(CurrentMp.formal().userId(), parseId(request.storeId()), key));
    }

    @PostMapping("/refund")
    public ApiResponse<VisitService.MyVisit> refund() {
        return ApiResponse.ok(visitService.refundUnused(CurrentMp.formal().userId()));
    }

    @PostMapping("/orders/{id}/mock-pay")
    public ApiResponse<OrderService.OrderView> mockPay(@org.springframework.web.bind.annotation.PathVariable String id) {
        return ApiResponse.ok(orderService.mockPay(CurrentMp.formal().userId(), parseId(id)));
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw BizException.badRequest("参数不正确");
        }
    }

    public record CreateRequest(@NotBlank String storeId) {
    }
}
