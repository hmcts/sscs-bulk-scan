package uk.gov.hmcts.reform.sscs.service.bulkscan;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.domain.bulkscan.CcdCallbackResponse;
import uk.gov.hmcts.reform.sscs.domain.bulkscan.ExceptionCaseData;

@Component
public class CcdCallbackHandler {

    public CcdCallbackResponse handle(
        ExceptionCaseData caseData,
        String userAuthToken,
        String serviceAuthToken,
        String userId
    ) {
        // TODO : Transform, Validate, Create Case, Case transition
        return CcdCallbackResponse.builder()
            .data(caseData.getCaseDetails().getCaseData())
            .build();
    }
}
