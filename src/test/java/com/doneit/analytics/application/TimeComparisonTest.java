package com.doneit.analytics.application;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TimeComparisonTest {
    @Test void deviationHandlesOverrunUnderrunAndZeroBaseline() {
        assertThat(new TimeComparison(600,840,1,0).deviationLabel()).isEqualTo("+40%");
        assertThat(new TimeComparison(600,300,1,0).deviationLabel()).isEqualTo("-50%");
        assertThat(new TimeComparison(60,60,1,0).deviationLabel()).isEqualTo("0%");
        assertThat(new TimeComparison(0,30,1,1).deviationLabel()).isEqualTo("-");
        assertThat(new TimeComparison(60,120,2,1).deviationPercent()).isNull();
        assertThat(TimeComparison.empty().add(new TimeComparison(60,120,1,0))).isEqualTo(new TimeComparison(60,120,1,0));
    }
    @Test void periodBoundariesHandleYearAndLeapMonth() {
        assertThat(AnalyticsPeriod.WEEK.start(LocalDate.of(2027,1,1))).isEqualTo(LocalDate.of(2026,12,28));
        assertThat(AnalyticsPeriod.WEEK.next(LocalDate.of(2026,12,28))).isEqualTo(LocalDate.of(2027,1,4));
        assertThat(AnalyticsPeriod.MONTH.next(LocalDate.of(2028,2,1))).isEqualTo(LocalDate.of(2028,3,1));
        assertThat(AnalyticsPeriod.MONTH.previous(LocalDate.of(2027,1,1))).isEqualTo(LocalDate.of(2026,12,1));
    }
}
