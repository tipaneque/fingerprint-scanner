package com.github.bluestring_digital.fingerprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class FingerprintApplication {

	public static void main(String[] args) {

		String jnaLibraryPath = System.getProperty("jna.library.path");
		if (jnaLibraryPath == null || jnaLibraryPath.isEmpty()) {
			System.setProperty("jna.library.path", "./lib");
		}

		System.out.println("===========================================");
		System.out.println("  Fingerprint Scanner API");
		System.out.println("  Starting application...");
		System.out.println("  JNA Library Path: " + System.getProperty("jna.library.path"));
		System.out.println("===========================================");

		SpringApplication.run(FingerprintApplication.class, args);

		System.out.println("===========================================");
		System.out.println("  Application started successfully!");
		System.out.println("  Access here: http://localhost:8080");
		System.out.println("  API Docs: http://localhost:8080/api/fingerprint/device/status");
		System.out.println("===========================================");
	}

	@Bean
	public WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/**")
						.allowedOrigins("*")
						.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
						.allowedHeaders("*")
						.maxAge(3600);
			}
		};
	}

}
