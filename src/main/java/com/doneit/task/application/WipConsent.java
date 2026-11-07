package com.doneit.task.application;

import com.doneit.task.domain.PlanningType;
import java.util.List;

/** Interactive adapters opt in; background jobs and MCP do not require modal approval. */
public interface WipConsent {
    boolean interactive();
    default PlanningType category(PlanningType value) { return value; }
    void confirm(List<WipService.Warning> warnings);
}
