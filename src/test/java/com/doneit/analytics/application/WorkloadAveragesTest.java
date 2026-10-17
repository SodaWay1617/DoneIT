package com.doneit.analytics.application;

import com.doneit.user.application.WorkloadSettings;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkloadAveragesTest {
    @Test void excludesUnknownDaysButIncludesKnownZeroDaysAndAveragesTotalIndependently() {
        var averages=view(List.of(day(1000,1000,1000,false),day(120,60,30,true),day(0,0,0,true)),
                new WorkloadSettings(480,720,840)).averages();
        assertThat(averages.sampleDays()).isEqualTo(2);
        assertThat(averages.scales()).extracting(WorkloadAnalyticsView.Scale::minutes).containsExactly(60L,30L,15L,105L);
        assertThat(averages.greenEnd()).isCloseTo(100.0/3,within(.001));
        assertThat(averages.yellowEnd()).isEqualTo(50);
    }
    @Test void handlesNoHistoryWithoutFabricatingAnAverage() {
        var averages=view(List.of(day(0,0,0,false)),new WorkloadSettings(480,720,840)).averages();
        assertThat(averages.available()).isFalse();
        assertThat(averages.sampleDays()).isZero();
    }
    @Test void respectsPersonalThresholdsAndClampsMarkersNotActualValues() {
        var averages=view(List.of(day(360,600,1800,true)),new WorkloadSettings(300,600,900)).averages();
        assertThat(averages.scales()).extracting(WorkloadAnalyticsView.Scale::level).containsExactly("yellow","yellow","red","red");
        assertThat(averages.scales().get(2).minutes()).isEqualTo(1800);
        assertThat(averages.scales().get(2).position()).isEqualTo(100);
        assertThat(averages.scales().get(2).overflow()).isTrue();
        assertThat(averages.greenEnd()).isCloseTo(300/14.4,within(.001));
    }
    @Test void zonesStayWithinTheDayEvenWhenThresholdsExceed24Hours() {
        var averages=view(List.of(day(0,0,0,true)),new WorkloadSettings(1440,1500,1600)).averages();
        assertThat(averages.greenEnd()).isEqualTo(100);
        assertThat(averages.yellowEnd()).isEqualTo(100);
        assertThat(averages.orangeEnd()).isEqualTo(100);
        assertThat(averages.scales().getFirst().position()).isZero();
    }
    private WorkloadAnalyticsView view(List<WorkloadAnalyticsView.Day> days,WorkloadSettings settings) {
        LocalDate date=LocalDate.of(2026,10,17);
        return new WorkloadAnalyticsView(date,date,days.size(),date,0,0,0,0,0,
                new PlanFactService.Accuracy(0,0,0,0,0),days,List.of(),settings,List.of());
    }
    private WorkloadAnalyticsView.Day day(long required,long wanted,long optional,boolean known) {
        return new WorkloadAnalyticsView.Day(LocalDate.of(2026,10,17),required,wanted,optional,0,known,"green",false);
    }
}
