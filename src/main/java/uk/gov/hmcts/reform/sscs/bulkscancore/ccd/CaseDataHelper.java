package uk.gov.hmcts.reform.sscs.bulkscancore.ccd;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

@Component
public class CaseDataHelper {
    private final CoreCaseDataApi coreCaseDataApi;

    @Value("${ccd.case.jurisdictionId}")
    private String jurisdictionId;

    @Value("${ccd.case.type}")
    private String caseType;

    @Value("${ccd.case.eventId}")
    private String eventId;

    public CaseDataHelper(CoreCaseDataApi coreCaseDataApi) {
        this.coreCaseDataApi = coreCaseDataApi;
    }

    public Long createCase(Map<String, Object> sscsCaseData, String userAuthToken, String serviceAuthToken, String userId) {
        StartEventResponse startEventResponse = coreCaseDataApi.startForCaseworker(
            userAuthToken, serviceAuthToken, userId, jurisdictionId, caseType, eventId
        );

        CaseDataContent caseDataContent = caseDataContent(sscsCaseData, startEventResponse.getToken());

        CaseDetails caseDetails = coreCaseDataApi.submitForCaseworker(
            userAuthToken, serviceAuthToken, userId, jurisdictionId, caseType, true, caseDataContent
        );

        return caseDetails.getId();
    }

    private CaseDataContent caseDataContent(Map<String, Object> sscsCaseData, String eventToken) {
        return CaseDataContent.builder()
            .data(sscsCaseData)
            .eventToken(eventToken)
            .event(Event.builder()
                .description("Case created from exception record")
                .id(eventId)
                .build()
            )
            .build();
    }
}
