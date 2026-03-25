package dev.danvega.deepresearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableResilientMethods
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}