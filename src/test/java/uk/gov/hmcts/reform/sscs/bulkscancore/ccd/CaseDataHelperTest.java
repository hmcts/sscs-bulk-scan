package uk.gov.hmcts.reform.sscs.bulkscancore.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.*;

import java.time.LocalDateTime;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.HttpServerErrorException;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.*;
import uk.gov.hmcts.reform.sscs.common.SampleCaseDataCreator;

@RunWith(SpringRunner.class)
public class CaseDataHelperTest {

    private static final String EVENT_TOKEN = "VALID_EVENT_TOKEN";

    private static final Long CASE_ID = Long.valueOf("1538992487551266");

    @Mock
    private CoreCaseDataApi coreCaseDataApi;

    private CaseDataHelper caseDataHelper;

    private SampleCaseDataCreator caseDataCreator = new SampleCaseDataCreator();

    @Before
    public void setUp() {
        caseDataHelper = new CaseDataHelper(
            coreCaseDataApi,
            "SSCS",
            "Benefit"
        );
    }

    @Test
    public void should_create_case_in_ccd_and_return_case_id() {
        // Given
        when(coreCaseDataApi.startForCaseworker(
            TEST_USER_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            TEST_USER_ID,
            "SSCS",
            "Benefit",
            "appealCreated")
        ).thenReturn(StartEventResponse.builder()
            .token(EVENT_TOKEN)
            .build());

        when(coreCaseDataApi.submitForCaseworker(
            TEST_USER_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            TEST_USER_ID,
            "SSCS",
            "Benefit",
            true,
            sscsCaseDataContent())
        ).thenReturn(CaseDetails.builder()
            .id(CASE_ID)
            .securityClassification(Classification.PUBLIC)
            .data(caseDataCreator.sscsCaseData())
            .createdDate(LocalDateTime.of(2018, 1, 1, 0, 0))
            .build());

        // When
        Long actualCaseId = caseDataHelper.createCase(
            caseDataCreator.sscsCaseData(),
            TEST_USER_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            TEST_USER_ID,
            "appealCreated"
        );

        // Then
        assertThat(actualCaseId).isEqualTo(CASE_ID);
    }

    @Test
    public void should_throw_server_exception_when_case_creation_fails_due_to_service_unavailable() {
        // Given
        when(coreCaseDataApi.startForCaseworker(
            TEST_USER_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            TEST_USER_ID,
            "SSCS",
            "Benefit",
            "appealCreated")
        ).thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        // When
        Throwable exception = Assertions.catchThrowable(() -> caseDataHelper.createCase(
            caseDataCreator.sscsCaseData(),
            TEST_USER_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            TEST_USER_ID,
            "appealCreated"
        ));

        // Then
        assertThat(exception).isInstanceOf(HttpServerErrorException.class);
        assertThat(exception).hasMessage("503 SERVICE_UNAVAILABLE");
    }


    private CaseDataContent sscsCaseDataContent() {
        return CaseDataContent.builder()
            .data(caseDataCreator.sscsCaseData())
            .eventToken(EVENT_TOKEN)
            .event(Event.builder()
                .description("Case created from exception record")
                .id("appealCreated")
                .build()
            )
            .securityClassification(Classification.PUBLIC)
            .build();
    }


}
