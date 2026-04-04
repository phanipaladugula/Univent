package com.univent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.core.userdetails.UserDetailsService;

@SpringBootApplication
public class BackendApplication {
	public static void main(String[] args) {
		var context = SpringApplication.run(BackendApplication.class, args);
		System.out.println("Beans of type UserDetailsService:");
		for (String name : context.getBeanNamesForType(UserDetailsService.class)) {
			System.out.println(name);
		}
	}
}