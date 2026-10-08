package com.gym.self.modules.order.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.order.application.OrderService;
import com.gym.self.modules.user.auth.CurrentMp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mp/orders")
public class MpOrderController {

    private final OrderService orderService;

    public MpOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderService.Created> create(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                    @Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(orderService.create(CurrentMp.formal().userId(), parseId(request.storeId()),
                parseId(request.cardProductId()), parseId(request.agreementId()), request.agreementVersion(),
                idempotencyKey));
    }

    @GetMapping
    public ApiResponse<List<OrderService.OrderView>> mine() {
        return ApiResponse.ok(orderService.mine(CurrentMp.formal().userId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderService.OrderView> detail(@PathVariable String id) {
        return ApiResponse.ok(orderService.detail(CurrentMp.formal().userId(), parseId(id)));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderService.OrderView> cancel(@PathVariable String id) {
        return ApiResponse.ok(orderService.cancel(CurrentMp.formal().userId(), parseId(id)));
    }

    @PostMapping("/{id}/mock-pay")
    public ApiResponse<OrderService.OrderView> mockPay(@PathVariable String id) {
        return ApiResponse.ok(orderService.mockPay(CurrentMp.formal().userId(), parseId(id)));
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw BizException.badRequest("参数不正确");
        }
    }

    public record CreateRequest(
            @NotBlank String storeId,
            @NotBlank String cardProductId,
            @NotBlank String agreementId,
            @NotNull Integer agreementVersion) {
    }
}
