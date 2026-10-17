package com.doneit.notifications.web;

import com.doneit.notifications.application.SiteReminderService;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SiteReminderController {
    private final SiteReminderService reminders;
    public SiteReminderController(SiteReminderService reminders) {this.reminders=reminders;}
    @GetMapping("/api/reminders")
    public ResponseEntity<SiteReminderService.Reminders> current(Locale locale) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reminders.current(locale));
    }
}
