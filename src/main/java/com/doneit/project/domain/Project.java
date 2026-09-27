package com.doneit.project.domain;

public record Project(Long id,String name,String code,String description,Long ownerUserId,boolean defaultProject,boolean owner) {}
