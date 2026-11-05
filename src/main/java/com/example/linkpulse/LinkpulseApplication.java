package com.example.linkpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class LinkpulseApplication {

	public static void main(String[] args) {
		SpringApplication.run(LinkpulseApplication.class, args);
	}

}
