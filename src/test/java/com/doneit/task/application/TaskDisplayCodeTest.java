package com.doneit.task.application;

import com.doneit.task.application.view.TaskListItemView;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TaskDisplayCodeTest {
    @Test void legacyAndScopedKeysDisplayOnlyTheOriginalCode() {
        for (String key : new String[]{"WORK-1___owner", "WORK-1___owner___project-123"})
            assertThat(TaskListItemView.displayTitle(key,"Task",true)).isEqualTo("WORK-1  Task");
        for (String key : new String[]{"MAIN-owner-1___owner", "MAIN-owner-1___owner___project-123"})
            assertThat(TaskListItemView.displayTitle(key,"Task",true)).isEqualTo("MAIN-1  Task");
        assertThat(TaskListItemView.displayTitle("WORK-1___owner___project-123","Task",false)).isEqualTo("Task");
    }
}
