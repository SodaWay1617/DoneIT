package com.doneit.analytics.application;

public record TimeComparison(long plannedMinutes, long actualMinutes, int completedUnits, int unestimatedUnits, int untrackedUnits) {
    public TimeComparison(long plannedMinutes,long actualMinutes,int completedUnits,int unestimatedUnits) {
        this(plannedMinutes,actualMinutes,completedUnits,unestimatedUnits,0);
    }
    public static TimeComparison empty() { return new TimeComparison(0,0,0,0); }
    public TimeComparison add(TimeComparison other) {
        return new TimeComparison(plannedMinutes+other.plannedMinutes,actualMinutes+other.actualMinutes,
                completedUnits+other.completedUnits,unestimatedUnits+other.unestimatedUnits,untrackedUnits+other.untrackedUnits);
    }
    public long differenceMinutes() { return actualMinutes-plannedMinutes; }
    public Long deviationPercent() {
        return plannedMinutes<=0 || unestimatedUnits>0 || untrackedUnits>0 ? null : Math.round(differenceMinutes()*100.0/plannedMinutes);
    }
    public String deviationLabel() {
        Long value=deviationPercent();
        return value==null ? "-" : (value>0?"+":"")+value+"%";
    }
    public String deviationClass() {
        return deviationPercent()==null ? "unknown" : differenceMinutes()>0 ? "over" : differenceMinutes()<0 ? "under" : "equal";
    }
}
