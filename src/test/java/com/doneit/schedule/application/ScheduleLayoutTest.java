package com.doneit.schedule.application;

import com.doneit.schedule.application.ScheduleView.*;
import com.doneit.task.domain.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ScheduleLayoutTest {
    private final LocalDate date = LocalDate.of(2026,4,8);
    private final Person person = new Person(1L,"owner");
    private Entry event(long id,String start,String end) {
        return new Entry(id,false,"Task "+id,date.atTime(LocalTime.parse(start)),date.atTime(LocalTime.parse(end)),
                PlanningType.REQUIRED,TaskStatus.IN_PROGRESS,List.of(person));
    }
    @Test void touchingIntervalsShareOneLane() {
        var column=ScheduleLayout.column(person,1L,date,List.of(event(1,"09:00","10:00"),event(2,"10:00","11:00")));
        assertThat(column.lanes()).isEqualTo(1);
        assertThat(column.blocks()).extracting(Block::lane).containsExactly(0,0);
    }
    @Test void widthEqualsPeakConcurrencyNotNumberOfTasks() {
        var column=ScheduleLayout.column(person,1L,date,List.of(event(1,"09:00","12:00"),event(2,"09:30","10:30"),
                event(3,"10:00","11:00"),event(4,"10:30","11:30"),event(5,"13:00","14:00")));
        assertThat(column.lanes()).isEqualTo(3);
        assertThat(column.blocks()).extracting(Block::lane).containsExactly(0,1,2,1,0);
        assertThat(column.widthPixels()).isEqualTo(672);
    }
    @Test void unrelatedPersonsDoNotIncreaseColumnWidth() {
        var event=event(1,"09:00","10:00");
        var column=ScheduleLayout.column(new Person(2L,"katya"),1L,date,List.of(event));
        assertThat(column.lanes()).isEqualTo(1);
        assertThat(column.blocks()).isEmpty();
        assertThat(column.mine()).isFalse();
    }
    @Test void overnightIntervalsAreClippedToDayBounds() {
        var event=new Entry(1L,false,"Night",date.minusDays(1).atTime(23,0),date.atTime(1,0),
                PlanningType.REQUIRED,TaskStatus.IN_PROGRESS,List.of(person));
        var block=ScheduleLayout.column(person,1L,date,List.of(event)).blocks().getFirst();
        assertThat(block.topMinutes()).isZero();
        assertThat(block.durationMinutes()).isEqualTo(60);
        assertThat(block.heightPixels()).isEqualTo(120);
    }
    @Test void implicitAndExplicitEndsAndMidnightAreCalculatedConsistently() {
        var start=date.atTime(23,30);
        assertThat(TaskInterval.endAt(start,null,90)).isEqualTo(date.plusDays(1).atTime(1,0));
        assertThat(TaskInterval.endAt(start,LocalTime.of(1,0),15)).isEqualTo(date.plusDays(1).atTime(1,0));
        assertThat(TaskInterval.minutes(LocalTime.of(23,30),LocalTime.of(1,0))).isEqualTo(90);
        assertThat(TaskInterval.endAt(start,null,null)).isNull();
        assertThatThrownBy(()->TaskInterval.validate(LocalTime.NOON,LocalTime.NOON)).isInstanceOf(IllegalArgumentException.class);
    }
}
