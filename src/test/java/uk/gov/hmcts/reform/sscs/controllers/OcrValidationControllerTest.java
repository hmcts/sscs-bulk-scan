package uk.gov.hmcts.reform.sscs.controllers;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus.*;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.authorisation.exceptions.InvalidTokenException;
import uk.gov.hmcts.reform.sscs.auth.AuthService;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CcdCallbackHandler;
import uk.gov.hmcts.reform.sscs.exceptions.ForbiddenException;
import uk.gov.hmcts.reform.sscs.exceptions.UnauthorizedException;

@RunWith(SpringRunner.class)
@WebMvcTest(OcrValidationController.class)
public class OcrValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CcdCallbackHandler handler;

    @MockBean
    private AuthService authService;

    @Test
    public void should_return_401_status_when_auth_service_throws_unauthenticated_exception() throws Exception {
        String requestBody = readResource("validation/valid-ocr-data.json");
        given(authService.authenticate("")).willThrow(UnauthorizedException.class);

        mockMvc
            .perform(
                post("/forms/SSCS1/validate-ocr")
                    .header("ServiceAuthorization", "")
                    .contentType(APPLICATION_JSON_VALUE)
                    .content(requestBody)
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void should_return_401_status_when_auth_service_throws_invalid_token_exception() throws Exception {
        String requestBody = readResource("validation/valid-ocr-data.json");
        given(authService.authenticate("test-token")).willThrow(InvalidTokenException.class);

        mockMvc
            .perform(
                post("/forms/SSCS1/validate-ocr")
                    .header("ServiceAuthorization", "test-token")
                    .contentType(APPLICATION_JSON_VALUE)
                    .content(requestBody)
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void should_return_403_status_when_auth_service_throws_forbidden_exception() throws Exception {
        String requestBody = readResource("validation/valid-ocr-data.json");
        doThrow(new ForbiddenException("Service does not have permissions to request case creation")).when(authService).assertIsAllowedToHandleCallback(any());

        mockMvc
            .perform(
                post("/forms/SSCS1/validate-ocr")
                    .header("ServiceAuthorization", "test-token")
                    .contentType(APPLICATION_JSON_VALUE)
                    .content(requestBody)
            )
            .andExpect(status().isForbidden())
            .andExpect(content().json("{\"error\":\"Service does not have permissions to request case creation\"}"));
    }

    @Test
    public void should_return_success_message_when_ocr_data_is_valid_for_sscs1() throws Exception {
        String requestBody = readResource("validation/valid-ocr-data.json");

        given(authService.authenticate("testServiceAuthHeader")).willReturn("testServiceName");

        given(handler.handleValidation(any()))
            .willReturn(CaseResponse.builder().warnings(emptyList()).errors(emptyList()).status(SUCCESS).build());

        mockMvc
            .perform(
                post("/forms/SSCS1/validate-ocr")
                    .contentType(APPLICATION_JSON_VALUE)
                    .header("ServiceAuthorization", "testServiceAuthHeader")
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON_VALUE))
            .andExpect(content().json(readResource("validation/valid-ocr-response.json")));
    }

    @Test
    public void should_return_success_message_when_ocr_data_is_valid_for_sscs1pe() throws Exception {
        String requestBody = readResource("validation/valid-ocr-data.json");

        given(authService.authenticate("testServiceAuthHeader")).willReturn("testServiceName");

        given(handler.handleValidation(any()))
            .willReturn(CaseResponse.builder().warnings(emptyList()).errors(emptyList()).status(SUCCESS).build());

        mockMvc
            .perform(
                post("/forms/SSCS1PE/validate-ocr")
                    .contentType(APPLICATION_JSON_VALUE)
                    .header("ServiceAuthorization", "testServiceAuthHeader")
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON_VALUE))
            .andExpect(content().json(readResource("validation/valid-ocr-response.json")));
    }

    @Test
    public void should_return_success_message_when_ocr_data_has_warnings() throws Exception {
        String requestBody = readResource("validation/valid-ocr-data.json");

        given(authService.authenticate("testServiceAuthHeader")).willReturn("testServiceName");

        given(handler.handleValidation(any()))
            .willReturn(CaseResponse.builder().warnings(newArrayList("warning1")).errors(emptyList()).status(WARNINGS).build());

        mockMvc
            .perform(
                post("/forms/SSCS1/validate-ocr")
                    .contentType(APPLICATION_JSON_VALUE)
                    .header("ServiceAuthorization", "testServiceAuthHeader")
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON_VALUE))
            .andExpect(content().json(readResource("validation/valid-ocr-response-warnings.json")));
    }

    @Test
    public void should_return_error_response_when_ocr_data_has_errors() throws Exception {
        String requestBody = readResource("validation/valid-ocr-data.json");

        given(authService.authenticate("testServiceAuthHeader")).willReturn("testServiceName");

        given(handler.handleValidation(any()))
            .willReturn(CaseResponse.builder().warnings(newArrayList(emptyList())).errors(newArrayList("error1")).status(ERRORS).build());

        mockMvc
            .perform(
                post("/forms/SSCS1/validate-ocr")
                    .contentType(APPLICATION_JSON_VALUE)
                    .header("ServiceAuthorization", "testServiceAuthHeader")
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON_VALUE))
            .andExpect(content().json(readResource("validation/valid-ocr-response-errors.json")));
    }

    @Test
    public void should_return_200_with_form_not_found_error_when_form_type_is_invalid() throws Exception {
        given(authService.authenticate("testServiceAuthHeader")).willReturn("testServiceName");

        mockMvc
            .perform(
                post("/forms/invalid-form-type/validate-ocr")
                    .contentType(APPLICATION_JSON_VALUE)
                    .header("ServiceAuthorization", "testServiceAuthHeader")
                    .content(readResource("validation/invalid/invalid-form-type.json"))
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON_VALUE))
            .andExpect(content().json(
                "{\"warnings\":[],\"errors\":[\"Form type 'invalid-form-type' not found\"],"
                    + "\"status\":\"ERRORS\"}"
            ));
    }

    @Test
    public void should_return_200_with_form_not_found_error_when_form_type_case_does_not_match() throws Exception {
        given(authService.authenticate("testServiceAuthHeader")).willReturn("testServiceName");
        mockMvc
            .perform(
                post("/forms/sscs1/validate-ocr") //Lowercase sscs1 is invalid
                    .contentType(APPLICATION_JSON_VALUE)
                    .header("ServiceAuthorization", "testServiceAuthHeader")
                    .content(readResource("validation/invalid/invalid-form-type.json"))
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON_VALUE))
            .andExpect(content()
                .json("{\"warnings\":[],\"errors\":[\"Form type 'sscs1' not found\"],\"status\":\"ERRORS\"}"));
    }

    @Test
    public void should_return_success_message_when_ocr_data_is_valid_for_sscs1u() throws Exception {
        String requestBody = readResource("validation/valid-ocr-data.json");

        given(authService.authenticate("testServiceAuthHeader")).willReturn("testServiceName");

        given(handler.handleValidation(any()))
            .willReturn(CaseResponse.builder().warnings(emptyList()).errors(emptyList()).status(SUCCESS).build());

        mockMvc
            .perform(
                post("/forms/SSCS1U/validate-ocr")
                    .contentType(APPLICATION_JSON_VALUE)
                    .header("ServiceAuthorization", "testServiceAuthHeader")
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON_VALUE))
            .andExpect(content().json(readResource("validation/valid-ocr-response.json")));
    }

    private String readResource(final String fileName) throws IOException {
        return Resources.toString(Resources.getResource(fileName), Charsets.UTF_8);
    }
}
