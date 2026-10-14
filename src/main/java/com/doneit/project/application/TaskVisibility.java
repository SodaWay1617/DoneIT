package com.doneit.project.application;

// SQL identifiers passed here are fixed application constants, never request values.
public final class TaskVisibility {
    private TaskVisibility() {}

    public static String visibleTo(String task, String viewer, boolean regular) {
        String column = regular ? "regular_task_id" : "task_id";
        return "(EXISTS (SELECT 1 FROM projects visibility_project WHERE visibility_project.id="
                + task + ".project_id AND visibility_project.task_visibility='ALL_MEMBERS') OR "
                + task + ".user_id=" + viewer
                + " OR EXISTS (SELECT 1 FROM task_assignees visibility_assignee WHERE visibility_assignee."
                + column + "=" + task + ".id AND visibility_assignee.user_id=" + viewer + "))";
    }

    public static String responsible(String task, String viewer, boolean regular) {
        String column = regular ? "regular_task_id" : "task_id";
        String assignments = "SELECT 1 FROM task_assignees responsible_assignee JOIN project_members responsible_member"
                + " ON responsible_member.user_id=responsible_assignee.user_id AND responsible_member.project_id="
                + task + ".project_id WHERE responsible_assignee." + column + "=" + task + ".id";
        return "(EXISTS (" + assignments + " AND responsible_assignee.user_id=" + viewer + ") OR ("
                + task + ".user_id=" + viewer + " AND NOT EXISTS (" + assignments + ")))";
    }

    public static String memberTasks(String task, boolean regular) {
        return memberTasks(task, regular, false);
    }

    public static String memberTasks(String task, boolean regular, boolean personal) {
        return "EXISTS (SELECT 1 FROM project_members access_member WHERE access_member.project_id="
                + task + ".project_id AND access_member.user_id=? AND "
                + visibleTo(task, "access_member.user_id", regular)
                + (personal ? " AND " + responsible(task, "access_member.user_id", regular) : "") + ")";
    }
}
