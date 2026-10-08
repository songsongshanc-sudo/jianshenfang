package com.gym.self.modules.order.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderCloseJob {

    private final OrderService orderService;

    public OrderCloseJob(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void closeExpired() {
        orderService.closeExpired();
    }
}
