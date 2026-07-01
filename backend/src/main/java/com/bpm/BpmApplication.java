package com.bpm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BpmApplication {
    public static void main(String[] args) {
        SpringApplication.run(BpmApplication.class, args);
    }
}
