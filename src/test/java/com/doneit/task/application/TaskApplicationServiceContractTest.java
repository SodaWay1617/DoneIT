package com.doneit.task.application;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskApplicationServiceContractTest {

    @Test
    void classUsesApplicationLevelValidationAndReadOnlyTransactions() {
        Validated validated = TaskApplicationService.class.getAnnotation(Validated.class);
        Transactional transactional = TaskApplicationService.class.getAnnotation(Transactional.class);

        assertNotNull(validated);
        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
    }

    @Test
    void writeUseCasesDeclareTransactionalBoundaryOnMethodLevel() throws Exception {
        Set<String> writeMethods = Set.of(
                "createTask",
                "createBacklogTask",
                "editTask",
                "markTaskAsDone",
                "markTaskAsClosed",
                "moveTask",
                "moveTaskToBacklog",
                "bulkMoveUnfinishedTasksToTomorrow"
        );

        for (Method method : TaskApplicationService.class.getDeclaredMethods()) {
            if (writeMethods.contains(method.getName())) {
                Transactional transactional = method.getAnnotation(Transactional.class);
                assertNotNull(transactional, method.getName() + " should define a write transaction boundary");
                assertEquals(false, transactional.readOnly(), method.getName() + " should not be read-only");
            }
        }
    }
}