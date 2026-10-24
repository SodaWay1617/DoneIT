package com.doneit.task.web;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CompactTaskRowTest {
    @Test void showsOnlyRealIntervalsAndExplicitEndWinsOverEstimate() {
        assertThat(CompactTaskRow.interval(null,null,120)).isEmpty();
        assertThat(CompactTaskRow.interval(LocalTime.of(9,0),null,null)).isEqualTo("09:00");
        assertThat(CompactTaskRow.interval(LocalTime.of(9,0),null,200)).isEqualTo("09:00–12:20");
        assertThat(CompactTaskRow.interval(LocalTime.of(9,0),LocalTime.of(10,0),200)).isEqualTo("09:00–10:00");
    }
    @Test void handlesMidnightAndMultiDayEstimates() {
        assertThat(CompactTaskRow.interval(LocalTime.of(23,30),null,90)).isEqualTo("23:30–01:00");
        assertThat(CompactTaskRow.nextDays(LocalTime.of(23,30),null,90)).isEqualTo(1);
        assertThat(CompactTaskRow.nextDays(LocalTime.of(23,30),LocalTime.of(1,0),null)).isEqualTo(1);
        assertThat(CompactTaskRow.nextDays(LocalTime.of(9,0),null,3000)).isEqualTo(2);
    }
}
