package com.doneit.task.application;

import com.doneit.project.application.ProjectService;
import com.doneit.tag.application.TagService;
import com.doneit.task.application.view.FinishedFilter;
import com.doneit.task.infrastructure.persistence.FinishedTaskRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinishedPageService {
    private final FinishedTaskRepository tasks;
    private final ProjectService projects;
    private final TagService tags;
    public FinishedPageService(FinishedTaskRepository tasks, ProjectService projects, TagService tags) {
        this.tasks = tasks; this.projects = projects; this.tags = tags;
    }
    public record PageNumber(Long number) {}
    public record Page(FinishedFilter filter, List<FinishedTaskRepository.Row> rows,
                       FinishedTaskRepository.Counts counts, long total, long number, long pages,
                       List<PageNumber> numbers) {
        public long from() { return total == 0 ? 0 : (number - 1) * filter.size() + 1; }
        public long to() { return Math.min(number * filter.size(), total); }
        public boolean previous() { return number > 1; }
        public boolean next() { return number < pages; }
        public String returnTo() { return filter.href(number); }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page page(FinishedFilter filter, long requestedPage) {
        if (!Set.of(25, 50, 100).contains(filter.size()) || requestedPage < 1
                || !Set.of("ALL", "DONE", "CLOSED").contains(filter.status()) || filter.tagIds().size() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (filter.projectId() != null && !projects.canAccess(filter.projectId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        for (Long id : filter.tagIds()) tags.get(id);
        var user = projects.user().id();
        var counts = tasks.counts(user, filter);
        long total = switch (filter.status()) { case "DONE" -> counts.done(); case "CLOSED" -> counts.closed(); default -> counts.total(); };
        long pages = Math.max(1, total / filter.size() + (total % filter.size() == 0 ? 0 : 1));
        long page = Math.min(requestedPage, pages);
        return new Page(filter, tasks.page(user, filter, page), counts, total, page, pages, numbers(page, pages));
    }

    private static List<PageNumber> numbers(long page, long pages) {
        var selected = new TreeSet<Long>(); selected.add(1L); selected.add(pages);
        for (long i = Math.max(1, page - 2); i <= Math.min(pages, page + 2); i++) selected.add(i);
        List<PageNumber> result = new ArrayList<>(); long previous = 0;
        for (long number : selected) {
            if (number > previous + 1) result.add(new PageNumber(null));
            result.add(new PageNumber(number)); previous = number;
        }
        return List.copyOf(result);
    }
}
