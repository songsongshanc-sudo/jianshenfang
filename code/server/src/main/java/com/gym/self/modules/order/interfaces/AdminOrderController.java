package com.gym.self.modules.order.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import com.gym.self.modules.order.application.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<List<OrderService.AdminOrder>> list(@RequestParam(required = false) String storeId) {
        return ApiResponse.ok(orderService.adminList(CurrentAdmin.get(), parseOptional(storeId)));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<OrderService.RefundResult> refund(@PathVariable String id) {
        return ApiResponse.ok(orderService.refund(CurrentAdmin.get(), parseId(id)));
    }

    private static Long parseOptional(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return parseId(id);
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw BizException.badRequest("参数不正确");
        }
    }
}
