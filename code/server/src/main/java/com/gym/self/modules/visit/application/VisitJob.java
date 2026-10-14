package com.gym.self.modules.visit.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VisitJob {

    private final VisitService visitService;

    public VisitJob(VisitService visitService) {
        this.visitService = visitService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void sweep() {
        visitService.sweep();
    }
}
