package uk.gov.hmcts.reform.sscs.controllers;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.hmcts.reform.authorisation.exceptions.InvalidTokenException;
import uk.gov.hmcts.reform.sscs.auth.AuthService;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CcdCallbackHandler;
import uk.gov.hmcts.reform.sscs.domain.transformation.CaseCreationDetails;
import uk.gov.hmcts.reform.sscs.domain.transformation.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.sscs.exceptions.ForbiddenException;
import uk.gov.hmcts.reform.sscs.exceptions.InvalidExceptionRecordException;
import uk.gov.hmcts.reform.sscs.exceptions.UnauthorizedException;

@SuppressWarnings("checkstyle:lineLength")
@RunWith(SpringRunner.class)
@WebMvcTest(TransformationController.class)
public class TransformationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CcdCallbackHandler ccdCallbackHandler;

    @MockBean
    private AuthService authService;

    @Test
    public void should_return_case_data_if_transformation_succeeded() throws Exception {
        given(authService.authenticate("testServiceAuthHeader")).willReturn("testServiceName");

        Map<String, Object> pairs = new HashMap<>();

        pairs.put("person1_first_name", "George");

        SuccessfulTransformationResponse transformationResult =
            new SuccessfulTransformationResponse(
                new CaseCreationDetails(
                    "case-type-id",
                    "event-id",
                    pairs
                ),
                asList(
                    "warning-1",
                    "warning-2"
                )
            );

        given(ccdCallbackHandler.handle(any())).willReturn(transformationResult);

        sendRequest("{}", "/transform-exception-record")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.case_creation_details.case_type_id").value("case-type-id"))
            .andExpect(jsonPath("$.case_creation_details.event_id").value("event-id"))
            .andExpect(jsonPath("$.case_creation_details.case_data.person1_first_name").value("George"))
            .andExpect(jsonPath("$.warnings[0]").value("warning-1"))
            .andExpect(jsonPath("$.warnings[1]").value("warning-2"));
    }

    @Test
    public void should_return_422_with_errors_if_transformation_failed() throws Exception {
        given(ccdCallbackHandler.handle(any()))
            .willThrow(new InvalidExceptionRecordException(
                asList(
                    "error-1",
                    "error-2"
                )
            ));

        sendRequest("{}", "/transform-exception-record")
            .andDo(print())
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0]").value("error-1"))
            .andExpect(jsonPath("$.errors[1]").value("error-2"));
    }

    @Test
    public void should_return_unauthorized_for_unauthorized_exception() throws Exception {
        given(authService.authenticate(any())).willThrow(new UnauthorizedException(null));

        HttpStatus status = UNAUTHORIZED;
        sendRequest("{}", "/transform-exception-record").andExpect(status().is(status.value()));
    }

    @Test
    public void should_return_unauthorized_for_invalid_exception() throws Exception {
        given(authService.authenticate(any())).willThrow(new InvalidTokenException(null));

        HttpStatus status = UNAUTHORIZED;
        sendRequest("{}","/transform-exception-record").andExpect(status().is(status.value()));
    }

    @Test
    public void should_return_unauthorized_for_forbidden_exception() throws Exception {
        given(authService.authenticate(any())).willThrow(new ForbiddenException(null));

        HttpStatus status = FORBIDDEN;
        sendRequest("{}", "/transform-exception-record").andExpect(status().is(status.value()));
    }

    @Test
    public void new_endpoint_should_return_case_data_if_transformation_succeeded() throws Exception {
        given(authService.authenticate("testServiceAuthHeader")).willReturn("testServiceName");

        Map<String, Object> pairs = new HashMap<>();

        pairs.put("person1_first_name", "George");

        SuccessfulTransformationResponse transformationResult =
            new SuccessfulTransformationResponse(
                new CaseCreationDetails(
                    "case-type-id",
                    "event-id",
                    pairs
                ),
                asList(
                    "warning-1",
                    "warning-2"
                )
            );

        given(ccdCallbackHandler.handle(any())).willReturn(transformationResult);

        sendRequest("{}", "/transform-scanned-data")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.case_creation_details.case_type_id").value("case-type-id"))
            .andExpect(jsonPath("$.case_creation_details.event_id").value("event-id"))
            .andExpect(jsonPath("$.case_creation_details.case_data.person1_first_name").value("George"))
            .andExpect(jsonPath("$.warnings[0]").value("warning-1"))
            .andExpect(jsonPath("$.warnings[1]").value("warning-2"));
    }

    @Test
    public void new_endpoint_should_return_422_with_errors_if_transformation_failed() throws Exception {
        given(ccdCallbackHandler.handle(any()))
            .willThrow(new InvalidExceptionRecordException(
                asList(
                    "error-1",
                    "error-2"
                )
            ));

        sendRequest("{}", "/transform-scanned-data")
            .andDo(print())
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0]").value("error-1"))
            .andExpect(jsonPath("$.errors[1]").value("error-2"));
    }

    @Test
    public void new_endpoint_should_return_unauthorized_for_unauthorized_exception() throws Exception {
        given(authService.authenticate(any())).willThrow(new UnauthorizedException(null));

        HttpStatus status = UNAUTHORIZED;
        sendRequest("{}", "/transform-scanned-data").andExpect(status().is(status.value()));
    }

    @Test
    public void new_endpoint_should_return_unauthorized_for_invalid_exception() throws Exception {
        given(authService.authenticate(any())).willThrow(new InvalidTokenException(null));

        HttpStatus status = UNAUTHORIZED;
        sendRequest("{}","/transform-scanned-data").andExpect(status().is(status.value()));
    }

    @Test
    public void new_endpoint_should_return_unauthorized_for_forbidden_exception() throws Exception {
        given(authService.authenticate(any())).willThrow(new ForbiddenException(null));

        HttpStatus status = FORBIDDEN;
        sendRequest("{}", "/transform-scanned-data").andExpect(status().is(status.value()));
    }

    private ResultActions sendRequest(String body, String url) throws Exception {
        return mockMvc
            .perform(
                post(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            );
    }

}
