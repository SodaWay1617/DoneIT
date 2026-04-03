package com.doneit.user.infrastructure.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordHashGenerator {

    private PasswordHashGenerator() {
    }

    public static void main(String[] args) {
        if (args.length != 1 || args[0].isBlank()) {
            System.err.println("Usage: PasswordHashGenerator <plain-password>");
            System.exit(1);
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode(args[0]));
    }
}
