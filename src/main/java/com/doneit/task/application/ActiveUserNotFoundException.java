package com.doneit.task.application;

public class ActiveUserNotFoundException extends RuntimeException {

    public ActiveUserNotFoundException() {
        super("Active user was not found");
    }
}