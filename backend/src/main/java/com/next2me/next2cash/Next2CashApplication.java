package com.next2me.next2cash;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
public class Next2CashApplication {
    public static void main(String[] args) {
        SpringApplication.run(Next2CashApplication.class, args);
    }
}
