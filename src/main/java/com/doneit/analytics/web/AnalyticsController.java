package com.doneit.analytics.web;

import com.doneit.analytics.application.AnalyticsService;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final Clock clock;
    public AnalyticsController(AnalyticsService analytics,Clock clock) {
        this.analytics=analytics; this.clock=clock;
    }
    @GetMapping("/analytics")
    public String analytics(@RequestParam(required=false) String month,Model model) {
        model.addAttribute("analytics",analytics.month(parse(month)));
        return "analytics";
    }
    private YearMonth parse(String value) {
        if(value==null||value.isBlank()) return YearMonth.now(clock);
        try { return YearMonth.parse(value); }
        catch(DateTimeParseException ignored) { return YearMonth.now(clock); }
    }
}
