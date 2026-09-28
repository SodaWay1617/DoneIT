package com.doneit.recurring.domain;

import static org.assertj.core.api.Assertions.*;
import com.doneit.task.domain.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class RegularTaskTest {
 @Test void occurrenceIsBoundedByActivationAndFinish(){
  RegularTask t=task(TaskStatus.DONE,RegularStatus.INACTIVE,LocalDateTime.of(2026,9,10,9,0),LocalDateTime.of(2026,9,12,18,0));
  assertThat(t.occursOn(LocalDate.of(2026,9,9))).isFalse();assertThat(t.occursOn(LocalDate.of(2026,9,10))).isTrue();
  assertThat(t.occursOn(LocalDate.of(2026,9,12))).isTrue();assertThat(t.occursOn(LocalDate.of(2026,9,13))).isFalse();
 }
 @Test void todoIsRejected(){assertThatThrownBy(()->task(TaskStatus.TODO,RegularStatus.INACTIVE,null,null)).isInstanceOf(IllegalArgumentException.class);}
 @Test void activeRequiresActivationTime(){assertThatThrownBy(()->task(TaskStatus.IN_PROGRESS,RegularStatus.ACTIVE,null,null)).isInstanceOf(IllegalArgumentException.class);}
 private static RegularTask task(TaskStatus s,RegularStatus rs,LocalDateTime active,LocalDateTime finished){return new RegularTask(1L,1L,1L,1L,"MAIN-u-1___u","Task",null,s,TaskPriority.NONE,RecurrenceType.DAILY,null,null,rs,active,finished,LocalDateTime.now(),LocalDateTime.now(),1);}
}
