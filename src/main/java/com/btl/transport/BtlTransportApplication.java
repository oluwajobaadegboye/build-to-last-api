package com.btl.transport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BtlTransportApplication {
    public static void main(String[] args) {
        SpringApplication.run(BtlTransportApplication.class, args);
    }
}
