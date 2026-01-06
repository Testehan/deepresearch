package com.testehan.deepresearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableResilientMethods
@EnableAsync
public class Application {

	static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}