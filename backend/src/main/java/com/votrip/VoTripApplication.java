package com.votrip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VoTripApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoTripApplication.class, args);
    }
}
