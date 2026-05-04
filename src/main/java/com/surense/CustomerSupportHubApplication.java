package com.surense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.surense")
public class CustomerSupportHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomerSupportHubApplication.class, args);
	}

}
