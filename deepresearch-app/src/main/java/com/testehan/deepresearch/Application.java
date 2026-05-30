package com.testehan.deepresearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableResilientMethods
@EnableAsync
@EnableScheduling
public class Application {

	static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}