package com.erp.backend_service.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tài liệu OpenAPI cho Swagger UI (http://localhost:8080/swagger-ui/index.html).
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI backendOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ERP Backend Service API")
                        .description("Tài liệu API")
                        .version("v1"));
    }
}
