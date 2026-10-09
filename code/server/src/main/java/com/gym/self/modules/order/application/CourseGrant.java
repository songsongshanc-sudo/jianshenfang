package com.gym.self.modules.order.application;

import com.gym.self.modules.order.domain.TradeOrder;

public interface CourseGrant {
    void onPaid(TradeOrder order);
}
