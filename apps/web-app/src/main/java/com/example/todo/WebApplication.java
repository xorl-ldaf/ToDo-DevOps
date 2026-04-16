package com.example.todo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.example.todo")
@EntityScan(basePackages = "com.example.todo.adapter.out.persistence.entity")
@EnableJpaRepositories(basePackages = "com.example.todo.adapter.out.persistence.repository")
public class WebApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
