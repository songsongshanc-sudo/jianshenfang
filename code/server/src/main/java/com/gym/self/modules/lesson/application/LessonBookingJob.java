package com.gym.self.modules.lesson.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LessonBookingJob {

    private final LessonBookingService lessonBookingService;

    public LessonBookingJob(LessonBookingService lessonBookingService) {
        this.lessonBookingService = lessonBookingService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void sweep() {
        lessonBookingService.sweepExpiredPending();
    }
}
