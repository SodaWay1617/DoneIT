package com.doneit.project.domain;

public record Project(Long id,String name,String code,String description,Long ownerUserId,boolean defaultProject,boolean owner, TaskVisibilityMode taskVisibility) {
    public Project(Long id, String name, String code, String description, Long ownerUserId, boolean defaultProject, boolean owner) {
        this(id, name, code, description, ownerUserId, defaultProject, owner, TaskVisibilityMode.ALL_MEMBERS);
    }

    @Override
    public String code() {
        return defaultProject ? "MAIN" : code;
    }
}
