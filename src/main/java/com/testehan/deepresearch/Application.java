package com.testehan.deepresearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableResilientMethods
public class Application {

	static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}