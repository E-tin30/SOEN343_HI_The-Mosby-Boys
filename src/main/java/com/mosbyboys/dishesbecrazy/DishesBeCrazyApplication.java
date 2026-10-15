package com.mosbyboys.dishesbecrazy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import com.mosbyboys.dishesbecrazy.application.reg.AuthService;

@SpringBootApplication
public class DishesBeCrazyApplication {

	public static void main(String[] args) {
		SpringApplication.run(DishesBeCrazyApplication.class, args);
	}

	@Bean
	CommandLineRunner testInsert(AuthService authService) {
		return args -> {
			authService.saveAccount();
		};
	}

}
