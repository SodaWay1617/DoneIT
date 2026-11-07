package com.doneit.task.application;

import com.doneit.task.application.WipService.*;
import com.doneit.task.domain.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WipRulesTest {
    final LocalDate day=LocalDate.of(2026,11,11);
    Plan plan(LocalDate date,Integer minutes,PlanningType type) {return new Plan(date,TaskStatus.TODO,minutes,type);}
    @Test void missingCategoryIsOptionalOnlyForWarningAndNoEstimateContributesNothing() {
        var after=plan(day,30,null);
        assertThat(WipService.evaluate(List.of(new Change(null,after)),Map.of(day,360L),360)).hasSize(1);
        assertThat(after.category()).isNull();
        assertThat(WipService.evaluate(List.of(new Change(null,plan(day,null,null))),Map.of(day,500L),360)).isEmpty();
    }
    @Test void aggregateOperationWarnsOnceForEachWorsenedDateAndUsesNetDelta() {
        var a=plan(day.minusDays(1),600,PlanningType.WANTED);
        var b=plan(day,400,PlanningType.WANTED);
        var changes=List.of(new Change(a,plan(day,600,PlanningType.WANTED)),new Change(b,plan(day.plusDays(1),400,PlanningType.WANTED)));
        var warnings=WipService.evaluate(changes,Map.of(day,400L,day.plusDays(1),360L),360);
        assertThat(warnings).extracting(Warning::date).containsExactly(day,day.plusDays(1));
        assertThat(warnings).extracting(Warning::added).containsExactly(200L,400L);
    }
    @Test void categoryReleaseWarnsButReductionsAndRequiredAdditionsDoNot() {
        var before=plan(day,100,PlanningType.REQUIRED);
        assertThat(WipService.evaluate(List.of(new Change(before,plan(day,100,PlanningType.OPTIONAL))),Map.of(day,500L),360)).hasSize(1);
        assertThat(WipService.evaluate(List.of(new Change(before,plan(day,50,PlanningType.OPTIONAL))),Map.of(day,500L),360)).isEmpty();
        assertThat(WipService.evaluate(List.of(new Change(null,before)),Map.of(day,500L),360)).isEmpty();
    }
    @Test void inactiveAndFinishedTasksDoNotConsumeCapacity() {
        for(var status:List.of(TaskStatus.NEW,TaskStatus.BACKLOG,TaskStatus.PAUSED,TaskStatus.DONE,TaskStatus.CLOSED))
            assertThat(new Plan(day,status,100,PlanningType.WANTED).contribution()).isZero();
        assertThat(plan(null,100,PlanningType.WANTED).contribution()).isZero();
    }
}
