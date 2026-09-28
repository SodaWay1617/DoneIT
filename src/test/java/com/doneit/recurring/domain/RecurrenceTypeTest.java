package com.doneit.recurring.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurrenceTypeTest {
 @Test void weekdaysExcludeWeekend(){assertThat(RecurrenceType.WEEKDAYS.occursOn(LocalDate.of(2026,9,28),null)).isTrue();assertThat(RecurrenceType.WEEKDAYS.occursOn(LocalDate.of(2026,10,3),null)).isFalse();}
 @Test void weeklyUsesAnchorWeekday(){LocalDate anchor=LocalDate.of(2026,9,28);assertThat(RecurrenceType.WEEKLY.occursOn(LocalDate.of(2026,10,5),anchor)).isTrue();assertThat(RecurrenceType.WEEKLY.occursOn(LocalDate.of(2026,10,6),anchor)).isFalse();}
 @Test void anchoredScheduleDoesNotProjectBeforeFirstDate(){LocalDate anchor=LocalDate.of(2026,10,5);assertThat(RecurrenceType.WEEKLY.occursOn(LocalDate.of(2026,9,28),anchor)).isFalse();}
 @Test void monthlyClampsToLastDay(){LocalDate anchor=LocalDate.of(2026,1,31);assertThat(RecurrenceType.MONTHLY.occursOn(LocalDate.of(2026,2,28),anchor)).isTrue();}
 @Test void leapDayClampsInNonLeapYear(){LocalDate anchor=LocalDate.of(2024,2,29);assertThat(RecurrenceType.YEARLY.occursOn(LocalDate.of(2026,2,28),anchor)).isTrue();}
}
