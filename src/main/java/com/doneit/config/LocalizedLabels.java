package com.doneit.config;

import com.doneit.recurring.domain.RecurrenceType;
import com.doneit.task.domain.TaskPriority;
import com.doneit.task.domain.TaskStatus;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component("labels")
public class LocalizedLabels {
    private final MessageSource messages;
    public LocalizedLabels(MessageSource messages) { this.messages = messages; }
    public String status(TaskStatus value) { return message("status." + value.name()); }
    public String priority(TaskPriority value) { return message("priority." + value.name()); }
    public String recurrence(RecurrenceType value) { return message("recurrence." + value.name()); }
    private String message(String key) {
        Locale locale = LocaleContextHolder.getLocale();
        return messages.getMessage(key, null, key, locale);
    }
}
