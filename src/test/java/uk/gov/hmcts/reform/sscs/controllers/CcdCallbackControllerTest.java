package uk.gov.hmcts.reform.sscs.controllers;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.auth.AuthService;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CcdCallbackHandler;
import uk.gov.hmcts.reform.sscs.common.SampleCaseDataCreator;
import uk.gov.hmcts.reform.sscs.exceptions.ForbiddenException;
import uk.gov.hmcts.reform.sscs.exceptions.UnauthorizedException;

@RunWith(SpringRunner.class)
@WebMvcTest(CcdCallbackController.class)
public class CcdCallbackControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CcdCallbackHandler ccdCallbackHandler;

    @MockBean
    private AuthService authService;

    private SampleCaseDataCreator caseDataCreator = new SampleCaseDataCreator();

    @Test
    public void should_successfully_handle_callback_and_return_exception_record_response() throws Exception {
        given(ccdCallbackHandler.handle(
            any(ExceptionCaseData.class),
            eq(Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build()))
        ).willReturn(AboutToStartOrSubmitCallbackResponse.builder()
            .data(caseDataCreator.exceptionCaseData())
            .build()
        );

        given(authService.authenticate("test-header"))
            .willReturn("some-service");

        doNothing().when(authService).assertIsAllowedToHandleCallback("some-service");

        mockMvc.perform(post("/exception-record")
            .contentType(MediaType.APPLICATION_JSON_UTF8)
            .header("Authorization", TEST_USER_AUTH_TOKEN)
            .header("serviceauthorization", TEST_SERVICE_AUTH_TOKEN)
            .header("user-id", TEST_USER_ID)
            .content(exceptionRecord("exceptionrecord.json")))
            .andExpect(jsonPath("$['data'].journeyClassification", is("New Application")))
            .andExpect(jsonPath("$['data'].poBoxJurisdiction", is("SSCS")))
            .andExpect(jsonPath("$['data'].poBox", is("SSCSPO")))
            .andExpect(jsonPath("$['data'].openingDate", is("2018-01-11")))

            .andExpect(jsonPath("$['data'].scanRecords[0].value.key", is("firstName")))
            .andExpect(jsonPath("$['data'].scanRecords[0].value.value", is("John")))
            .andExpect(jsonPath("$['data'].scanRecords[0].id", is("d55a7f14-92c3-4134-af78-f2aa2b201841")))

            .andExpect(jsonPath("$['data'].scanRecords[1].value.key", is("lastName")))
            .andExpect(jsonPath("$['data'].scanRecords[1].value.value", is("Smith")))
            .andExpect(jsonPath("$['data'].scanRecords[1].id", is("d55a7f14-92c3-4134-af78-f2aa2b201841")));
    }

    @Test
    public void should_throw_exception_when_handler_fails() throws Exception {
        given(ccdCallbackHandler.handle(
            any(ExceptionCaseData.class),
            eq(Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build()))
        ).willThrow(RuntimeException.class);

        given(authService.authenticate("test-header"))
            .willReturn("some-service");

        doNothing().when(authService).assertIsAllowedToHandleCallback("some-service");

        mockMvc.perform(post("/exception-record")
            .contentType(MediaType.APPLICATION_JSON_UTF8)
            .header("Authorization", TEST_USER_AUTH_TOKEN)
            .header("serviceauthorization", TEST_SERVICE_AUTH_TOKEN)
            .header("user-id", TEST_USER_ID)
            .content(exceptionRecord("exceptionrecord.json")))
            .andExpect(status().is5xxServerError());
    }

    @Test
    public void should_throw_unauthenticated_exception_when_auth_header_is_missing() throws Exception {
        // given
        willThrow(UnauthorizedException.class)
            .given(authService).authenticate(null);

        // when
        mockMvc.perform(post("/exception-record")
            .contentType(MediaType.APPLICATION_JSON_UTF8)
            .header("Authorization", TEST_USER_AUTH_TOKEN)
            .header("user-id", TEST_USER_ID)
            .content(exceptionRecord("exceptionrecord.json")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void should_throw_unauthorized_exception_when_auth_header_is_missing() throws Exception {
        // given
        willThrow(ForbiddenException.class)
            .given(authService).authenticate(null);

        // when
        mockMvc.perform(post("/exception-record")
            .contentType(MediaType.APPLICATION_JSON_UTF8)
            .header("Authorization", TEST_USER_AUTH_TOKEN)
            .header("Authorization", TEST_USER_AUTH_TOKEN)
            .header("user-id", TEST_USER_ID)
            .content(exceptionRecord("exceptionrecord.json")))
            .andExpect(status().isForbidden());
    }
}
