package com.doneit.mcp.application;

import com.doneit.task.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class McpTaskInputTest {
    final ObjectMapper json=new ObjectMapper();
    final LocalDateTime now=LocalDateTime.of(2026,10,27,9,0);
    final Task original=new Task(1L,2L,"Original","Keep",TaskStatus.TODO,now,null,now,now,null,null,
            3L,1L,"WORK-1",null,TaskPriority.HIGH,60,25,PlanningType.WANTED,LocalTime.of(10,0));

    @Test void patchPreservesOmittedFieldsAndExplicitNullClearsOnlyNullableValues() throws Exception {
        var patch=McpTaskInput.merge(json.readTree("{\"title\":\"Renamed\"}"),original,null);
        assertThat(patch.title()).isEqualTo("Renamed");
        assertThat(patch.description()).isEqualTo("Keep");
        assertThat(patch.estimateMinutes()).isEqualTo(60);
        assertThat(patch.endTime()).isEqualTo(LocalTime.of(10,0));
        assertThat(patch.planningType()).isEqualTo(PlanningType.WANTED);
        assertThat(McpTaskInput.merge(json.readTree("{\"description\":null}"),original,null).description()).isNull();
        assertThatThrownBy(()->McpTaskInput.merge(json.readTree("{\"title\":null}"),original,null)).isInstanceOf(McpFailure.class);
    }

    @Test void switchingPlanningRequiresExplicitClearingAndRespectsIntervals() throws Exception {
        assertThatThrownBy(()->McpTaskInput.merge(json.readTree("{\"plannedDate\":\"2026-10-28\"}"),original,null)).isInstanceOf(McpFailure.class);
        var changed=McpTaskInput.merge(json.readTree("{\"plannedDate\":\"2026-10-28\",\"plannedForAt\":null,\"endTime\":null}"),original,null);
        assertThat(changed.plannedDate()).isEqualTo(LocalDate.of(2026,10,28));
        assertThat(changed.plannedForAt()).isNull();
        assertThat(changed.estimateMinutes()).isEqualTo(60);
        var interval=McpTaskInput.merge(json.readTree("{\"plannedForAt\":\"2026-10-28T09:30\"}"),original,null);
        assertThat(interval.estimateMinutes()).isEqualTo(30);
        assertThatThrownBy(()->McpTaskInput.merge(json.readTree("{\"endTime\":\"10:30\",\"estimateMinutes\":20}"),original,null)).isInstanceOf(McpFailure.class);
    }

    @Test void rejectsProtectedFieldsAndCreatesInboxByDefault() throws Exception {
        for(String value:new String[]{"{\"spentMinutes\":0}","{\"userId\":2}","{\"estimateMinutes\":0}","{\"projectId\":1.5}","{\"plannedDate\":\"tomorrow\"}"})
            assertThatThrownBy(()->McpTaskInput.merge(json.readTree(value),original,null)).isInstanceOf(McpFailure.class);
        var created=McpTaskInput.merge(json.readTree("{\"title\":\"Buy tickets\"}"),null,4L);
        assertThat(created.status()).isEqualTo(TaskStatus.NEW);
        assertThat(created.projectId()).isEqualTo(4L);
        assertThat(created.plannedDate()).isNull();
        assertThat(created.plannedForAt()).isNull();
        assertThat(created.estimateMinutes()).isNull();
        assertThatThrownBy(()->McpTaskInput.merge(json.readTree("{\"title\":\"Finished\",\"status\":\"CLOSED\"}"),null,4L)).isInstanceOf(McpFailure.class);
    }
}
