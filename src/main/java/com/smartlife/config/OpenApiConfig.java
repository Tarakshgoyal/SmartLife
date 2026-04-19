package com.smartlife.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SmartLife Hub API",
                version = "1.0.0",
                description = """
                        AI-Powered Personal Life Management Platform.

                        **Modules:**
                        - `/api/v1/auth` — Authentication (JWT)
                        - `/api/v1/documents` — Document Intelligence (OCR + ML Classification)
                        - `/api/v1/expenses` — Expense Tracking (ML Categorization + Anomaly Detection)
                        - `/api/v1/health` — Health Pattern Analysis
                        - `/api/v1/automation` — Reminders & Notifications
                        - `/api/v1/analytics` — Dashboard & Life Score
                        - `/api/v1/reports` — Weekly Reports

                        **WebSocket:** Connect to `/ws` (SockJS) and subscribe to `/user/queue/notifications`
                        """,
                contact = @Contact(name = "SmartLife Hub", email = "support@smartlife.app")
        ),
        servers = {
                @Server(url = "http://localhost:9090", description = "Local"),
                @Server(url = "https://api.smartlife.app", description = "Production")
        },
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Enter JWT token obtained from POST /api/v1/auth/login"
)
public class OpenApiConfig {

    /**
     * Expose a primary ObjectMapper bean — Spring Boot 4.x no longer auto-registers
     * one as a general-purpose injectable bean; we define it explicitly here so any
     * service that needs JSON serialisation can @Autowired / constructor-inject it.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
