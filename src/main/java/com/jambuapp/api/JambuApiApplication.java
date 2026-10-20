package com.jambuapp.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// A identidade é resolvida exclusivamente pelas sessões persistidas da aplicação.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class JambuApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(JambuApiApplication.class, args);
	}

}
