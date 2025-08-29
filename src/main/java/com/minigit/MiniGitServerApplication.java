package com.minigit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mini Git Server Application
 * 
 * 迷你Git服务器主应用类
 * 提供基于HTTP协议的Git Smart HTTP服务
 */
@SpringBootApplication
public class MiniGitServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniGitServerApplication.class, args);
    }
}