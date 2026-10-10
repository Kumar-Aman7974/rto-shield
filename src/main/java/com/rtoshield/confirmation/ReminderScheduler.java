package com.rtoshield.confirmation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * STUB (M5, Person B).
 * TODO(B): find orders in AWAITING_CONFIRMATION older than config confirm.reminder_minutes with
 * reminderSentAt == null, send a reminder, set reminderSentAt. If still silent after a second
 * wait: set status ON_HOLD and offer to pay online.
 */
@Component
@Slf4j
public class ReminderScheduler {

    @Scheduled(fixedDelay = 60_000)
    public void sendReminders() {
        // nothing yet
    }
}
