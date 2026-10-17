package com.doneit.analytics.application;

public enum ReportType {
    DAY,WEEK,MONTH,PROJECT;
    public AnalyticsPeriod period() { return AnalyticsPeriod.valueOf(name()); }
}
