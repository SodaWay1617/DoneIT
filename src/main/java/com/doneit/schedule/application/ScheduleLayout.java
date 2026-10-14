package com.doneit.schedule.application;

import com.doneit.schedule.application.ScheduleView.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ScheduleLayout {
    private ScheduleLayout() {}

    public static Column column(Person person, Long viewer, LocalDate date, List<Entry> entries) {
        LocalDateTime start = date.atStartOfDay(), end = start.plusDays(1);
        List<Entry> events = entries.stream().filter(e -> e.start() != null && e.end() != null)
                .filter(e -> e.start().isBefore(end) && e.end().isAfter(start))
                .filter(e -> e.people().stream().anyMatch(p -> p.id().equals(person.id())))
                .sorted(Comparator.comparing(Entry::start).thenComparing(Entry::end, Comparator.reverseOrder())
                        .thenComparing(Entry::title).thenComparing(Entry::id)).toList();
        List<LocalDateTime> laneEnds = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();
        for (Entry event : events) {
            int lane = 0;
            while (lane < laneEnds.size() && laneEnds.get(lane).isAfter(event.start())) lane++;
            if (lane == laneEnds.size()) laneEnds.add(event.end());
            else laneEnds.set(lane, event.end());
            LocalDateTime clippedStart = event.start().isBefore(start) ? start : event.start();
            LocalDateTime clippedEnd = event.end().isAfter(end) ? end : event.end();
            int top = (int) ChronoUnit.MINUTES.between(start, clippedStart);
            int duration = Math.max(1, (int) ChronoUnit.MINUTES.between(clippedStart, clippedEnd));
            blocks.add(new Block(event, lane, top, duration));
        }
        return new Column(person, person.id().equals(viewer), Math.max(1, laneEnds.size()), List.copyOf(blocks));
    }
}
