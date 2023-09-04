package uk.gov.hmcts.reform.sscs.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfiguration {

    @Bean
    public OpenAPI trackYourAppealNotificationsApi() {
        return new OpenAPI()
            .info(new Info().title("Bulk Scan transformation for SSCS")
                .description("Project to manage the Bulk Scan transformation for SSCS")
                .version("v0.0.1")
                .license(new License().name("Apache 2.0").url("http://springdoc.org")));
    }

}
