package dev.syaz.library.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {
    @Bean
    OpenAPI libraryOpenApi() {
        Info info = new Info()
                .title("Library API")
                .version("v1")
                .description("Registers borrowers and physical book copies, manages loans, and preserves loan history. "
                        + "The assessment API assumes a trusted caller; "
                        + "borrower IDs select the borrower on whose behalf an action is performed.");
        return new OpenAPI().info(info);
    }
}
