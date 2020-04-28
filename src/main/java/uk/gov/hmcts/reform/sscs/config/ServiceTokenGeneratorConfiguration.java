package uk.gov.hmcts.reform.sscs.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGeneratorFactory;

@Configuration
@Lazy
@EnableFeignClients(basePackageClasses = ServiceAuthorisationApi.class)
@Slf4j
public class ServiceTokenGeneratorConfiguration {

    @Bean
    public AuthTokenGenerator authTokenGenerator(
        @Value("${idam.s2s-auth.totp_secret}") final String secret,
        @Value("${idam.s2s-auth.microservice}") final String microService,
        final ServiceAuthorisationApi serviceAuthorisationApi
    ) {
        log.info("****** totp_secret: " + secret);
        log.info("****** microService: " + microService);
        return AuthTokenGeneratorFactory.createDefaultGenerator(secret, microService, serviceAuthorisationApi);
    }

}
