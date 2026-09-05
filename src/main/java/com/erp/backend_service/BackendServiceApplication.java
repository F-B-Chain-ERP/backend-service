package com.erp.backend_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Lớp khởi động ứng dụng backend ERP. Quét entity, repository và bật JPA Auditing.
 */
@SpringBootApplication
@EntityScan(basePackages = {"com.erp.core.domain", "com.erp.backend_service"})
@EnableJpaRepositories(basePackages = "com.erp.backend_service.repository")
@EnableJpaAuditing
@EnableScheduling
public class BackendServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendServiceApplication.class, args);
	}

}
