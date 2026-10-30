package com.navan.travel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

@SpringBootApplication
public class TravelCommerceApplication {

    public static void main(String[] args) {
        new File("data").mkdirs();
        SpringApplication.run(TravelCommerceApplication.class, args);
    }
}
