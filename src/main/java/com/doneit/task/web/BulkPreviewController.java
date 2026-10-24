package com.doneit.task.web;

import com.doneit.task.application.TaskApplicationService;
import com.doneit.task.application.TaskApplicationService.BulkPreview;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class BulkPreviewController {
    private final TaskApplicationService tasks;
    public BulkPreviewController(TaskApplicationService tasks) { this.tasks=tasks; }
    @GetMapping("/api/tasks/bulk-preview")
    public ResponseEntity<BulkPreview> preview(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) {
        if(date.getYear()<1||date.getYear()>9998) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tasks.previewBulkMoves(date));
    }
}
