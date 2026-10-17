package com.spectrace;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@MapperScan("com.spectrace.mapper")
@SpringBootApplication
public class SpecTraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpecTraceApplication.class, args);
    }
}
