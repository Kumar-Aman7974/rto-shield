package com.rtoshield.recovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * STUB (M7, Person B). This is PS7's "proactive rescue".
 * TODO(B): use OrderRepository.findByStatusInAndLastEventAtBefore(SHIPPED / OUT_FOR_DELIVERY / RESCHEDULED,
 * now minus config delay.alert_minutes). For each, send a status-check or reschedule message once.
 */
@Component
@Slf4j
public class DelayMonitorScheduler {

    @Scheduled(fixedDelay = 60_000)
    public void checkDelays() {
        // nothing yet
    }
}
