package com.enterprise.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EnterpriseChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseChatApplication.class, args);
    }
}
