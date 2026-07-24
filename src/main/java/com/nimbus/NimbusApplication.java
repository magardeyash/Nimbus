package com.nimbus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement(order = 10000)
public class NimbusApplication {
    public static void main(String[] args) {
        SpringApplication.run(NimbusApplication.class, args);
    }
}