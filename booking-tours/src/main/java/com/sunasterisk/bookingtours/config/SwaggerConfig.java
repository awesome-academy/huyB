package com.sunasterisk.bookingtours.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SUN Booking Tours API")
                        .description("REST API documentation — SUN Booking Tours mock project")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("JWT Cookie"))
                .components(new Components()
                        .addSecuritySchemes("JWT Cookie",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name("JWT_TOKEN")
                                        .description("HttpOnly JWT cookie — tự động gửi kèm mọi request sau đăng nhập")));
    }
}
