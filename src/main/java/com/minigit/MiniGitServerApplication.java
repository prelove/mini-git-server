package com.minigit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mini Git Server application.
 * Provides Git Smart HTTP over HTTP.
 */
@SpringBootApplication
public class MiniGitServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniGitServerApplication.class, args);
    }
}
