package uk.gov.hmcts.reform.sscs.service.bulkscan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sscs.common.SampleCaseDataCreator;
import uk.gov.hmcts.reform.sscs.common.TestHelper;
import uk.gov.hmcts.reform.sscs.domain.bulkscan.CaseDetails;
import uk.gov.hmcts.reform.sscs.domain.bulkscan.CcdCallbackResponse;
import uk.gov.hmcts.reform.sscs.domain.bulkscan.ExceptionCaseData;

@RunWith(SpringRunner.class)
public class CcdCallbackHandlerTest {

    private CcdCallbackHandler ccdCallbackHandler = new CcdCallbackHandler();

    private SampleCaseDataCreator caseDataCreator = new SampleCaseDataCreator();

    // TODO Currently handler is not doing much will add more tests in upcoming PR's
    @Test
    public void should_successfully_handle_exception_record_and_return_case_data() {
        // given
        CaseDetails caseDetails = CaseDetails
            .builder()
            .caseData(caseDataCreator.caseData())
            .state("ScannedRecordReceived")
            .build();

        // when
        CcdCallbackResponse ccdCallbackResponse = ccdCallbackHandler.handle(
            ExceptionCaseData.builder()
                .caseDetails(caseDetails)
                .eventId("createNewCase")
                .build(),
            TestHelper.TEST_USER_AUTH_TOKEN,
            TestHelper.TEST_SERVICE_AUTH_TOKEN,
            TestHelper.TEST_USER_ID
        );

        // then
        Map<String, Object> expectedCaseData = caseDataCreator.caseData();

        assertThat(ccdCallbackResponse.getData()).containsAllEntriesOf(expectedCaseData);

    }
}
