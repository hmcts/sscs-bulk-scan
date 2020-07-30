package uk.gov.hmcts.reform.sscs.validators;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.contains;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class PostcodeValidator {
    private static final String POSTCODE_RESULT = "true";
    private final String url;
    private final boolean enabled;
    private final RestTemplate restTemplate;

    @Autowired
    public PostcodeValidator(
        @Value("${postcode-validator.url}") final String url,
        @Value("${postcode-validator.enabled}")final boolean enabled,
        RestTemplate restTemplate) {
        this.url = url;
        this.enabled = enabled;
        this.restTemplate = restTemplate;
    }

    public boolean isValid(String postCode) {
        if (!enabled) {
            log.info("PostcodeValidator is not enabled");
            return true;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(headers);
        try {
            ResponseEntity<byte[]> response = restTemplate
                .exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    byte[].class,
                    postCode
                );
            logIfNotValidPostCode(postCode, response.getStatusCodeValue());
            return response.getStatusCodeValue() == 200 && nonNull(response.getBody()) && contains(new String(response.getBody()), POSTCODE_RESULT);

        } catch (RestClientResponseException e) {
            if (e.getRawStatusCode() != 200) {
                log.info("Post code search returned statusCode {} for postcode {}", e.getRawStatusCode(), postCode);
            }
            return false;
        }
    }

    private void logIfNotValidPostCode(String postCode, int statusCode) {
        if (statusCode != 200) {
            log.info("Post code search returned statusCode {} for postcode {}", statusCode, postCode);
        }
    }


}
