package com.rtoshield;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RtoShieldApplication {
    public static void main(String[] args) {
        SpringApplication.run(RtoShieldApplication.class, args);
    }
}
