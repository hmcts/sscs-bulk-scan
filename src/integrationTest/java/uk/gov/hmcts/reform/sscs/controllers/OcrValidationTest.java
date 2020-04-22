package uk.gov.hmcts.reform.sscs.controllers;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static uk.gov.hmcts.reform.sscs.helper.TestConstants.SERVICE_AUTH_TOKEN;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;


@AutoConfigureMockMvc
@ContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application_it.yaml")
class OcrValidationTest  {

    @Autowired
    private MockMvc mvc;

    @MockBean
    protected AuthTokenValidator authTokenValidator;

    @Test
    void should_return_200_when_ocr_form_validation_request_data_is_valid() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("validation/valid-ocr-data.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.warnings", nullValue()))
            .andExpect(jsonPath("$.errors", nullValue()));
    }

    @Test
    void should_return_401_when_service_auth_header_is_missing() throws Throwable {
        String content = readResource("validation/valid-ocr-data.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isUnauthorized())
            .andExpect(content().json("{\"error\":\"Missing ServiceAuthorization header\"}"));

    }

    private String readResource(final String fileName) throws IOException {
        return Resources.toString(Resources.getResource(fileName), Charsets.UTF_8);
    }
}
