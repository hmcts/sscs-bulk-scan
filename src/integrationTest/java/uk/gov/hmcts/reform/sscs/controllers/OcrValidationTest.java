package uk.gov.hmcts.reform.sscs.controllers;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.idam.IdamService;

@AutoConfigureMockMvc
@ContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application_it.yaml")
public class OcrValidationTest  {

    @Autowired
    private MockMvc mvc;

    @MockBean
    protected AuthTokenValidator authTokenValidator;

    @MockBean
    protected IdamService idamService;

    @MockBean
    protected CcdService ccdService;

    @Test
    public void should_return_200_when_ocr_form_validation_request_data_is_valid() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/valid-ocr-data.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.warnings", hasSize(0)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    public void should_return_200_when_ocr_form_for_uc_validation_request_data_is_valid() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/valid-ocr-data-for-uc.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.warnings", hasSize(0)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    public void should_return_200_when_ocr_form_with_address_line3_blank_validation_request_data_is_valid() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/valid-ocr-data-address-line3.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.warnings", hasSize(0)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    public void should_return_200_when_ocr_form_with_hearing_sub_type_validation_request_data_is_valid() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/valid-ocr-data-with-hearing-sub-type.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.warnings", hasSize(0)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    public void should_return_200_with_status_warnings_when_ocr_form_validation_request_data_is_incomplete() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/incomplete-ocr-data.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WARNINGS"))
            .andExpect(jsonPath("$.warnings", hasSize(7)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    public void should_return_200_when_ocr_form_with_form_type_sscs1u_and_hearing_sub_type_validation_request_data_are_empty() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/invalid-ocr-data-with-hearing-sub-type-sscs1u.json");

        mvc.perform(
            post("/forms/SSCS1U/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WARNINGS"))
            .andExpect(jsonPath("$.warnings", hasSize(1)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    public void should_return_200_when_ocr_form_with_form_type_sscs1peu_and_hearing_sub_type_validation_request_data_are_empty() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/invalid-ocr-data-with-hearing-sub-type-sscs1peu.json");

        mvc.perform(
            post("/forms/SSCS1PEU/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WARNINGS"))
            .andExpect(jsonPath("$.warnings", hasSize(1)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    public void should_return_200_when_ocr_form_with_form_type_sscs1_and_hearing_sub_type_validation_request_data_are_empty() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/invalid-ocr-data-with-hearing-sub-type.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.warnings", hasSize(0)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    public void should_return_200_with_status_errors_when_ocr_form_validation_request_fails_schema_validation() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/invalid-ocr-data-fails-schema.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ERRORS"))
            .andExpect(jsonPath("$.warnings", hasSize(0)))
            .andExpect(jsonPath("$.errors", hasSize(1)));
    }

    @Test
    //Convert errors with transforming data to warnings during validation endpoint
    public void should_return_200_with_status_warnings_when_ocr_form_validation_request_has_errors_with_transformation() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/invalid-ocr-data-transformation.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WARNINGS"))
            .andExpect(jsonPath("$.warnings", hasSize(1)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    //Convert errors with the validating data to warnings during validation endpoint
    public void should_return_200_with_status_warnings_when_ocr_form_validation_request_has_errors_with_validation() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/invalid-ocr-data-validation.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WARNINGS"))
            .andExpect(jsonPath("$.warnings", hasSize(1)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    public void should_return_200_with_status_warnings_when_ocr_form_validation_request_has_duplicate_case_warnings() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        SscsCaseDetails caseDetails = SscsCaseDetails.builder().id(1L).build();

        given(ccdService.findCcdCaseByNinoAndBenefitTypeAndMrnDate(anyString(), anyString(), anyString(), any())).willReturn(caseDetails);
        String content = readResource("mappings/ocr-validation/valid-ocr-data.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WARNINGS"))
            .andExpect(jsonPath("$.warnings", hasSize(1)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    public void should_return_401_when_service_auth_header_is_missing() throws Throwable {
        String content = readResource("mappings/ocr-validation/valid-ocr-data.json");

        mvc.perform(
            post("/forms/SSCS1/validate-ocr")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isUnauthorized())
            .andExpect(content().json("{\"error\":\"Missing ServiceAuthorization header\"}"));

    }

    @Test
    public void fuzzyMatchingMaternityAllowanceBenefitTypeForSscs1uForm() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/valid-ocr-data.json");
        content = content.replaceAll("benefit_type_description", "benefit_type_other");
        content = content.replaceAll("PIP", "Maternity something");

        mvc.perform(
            post("/forms/SSCS1U/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.warnings", hasSize(0)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    public void fuzzyMatchingInvalidNameBenefitTypeForSscs1uForm() throws Throwable {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String content = readResource("mappings/ocr-validation/valid-ocr-data.json");
        content = content.replaceAll("benefit_type_description", "benefit_type_other");
        content = content.replaceAll("PIP", "invalid name");

        mvc.perform(
            post("/forms/SSCS1U/validate-ocr")
                .header("ServiceAuthorization", SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WARNINGS"))
            .andExpect(jsonPath("$.warnings", hasSize(1)))
            .andExpect(jsonPath("$.errors", hasSize(0)))
            .andExpect(content().json("{\"warnings\":[\"benefit_type_other is empty\"],\"errors\":[],\"status\":\"WARNINGS\"}"));
    }

    private String readResource(final String fileName) throws IOException {
        return Resources.toString(Resources.getResource(fileName), Charsets.UTF_8);
    }
}
