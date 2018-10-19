package uk.gov.hmcts.reform.sscs.bulkscancore.ccd;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.*;

@Component
public class CaseDataHelper {
    private final CoreCaseDataApi coreCaseDataApi;

    private final String jurisdiction;

    private final String caseType;

    private final String eventId;

    public CaseDataHelper(
        CoreCaseDataApi coreCaseDataApi,
        @Value("${ccd.case.jurisdiction}") String jurisdiction,
        @Value("${ccd.case.type}") String caseType,
        @Value("${ccd.case.eventId}") String eventId
    ) {
        this.coreCaseDataApi = coreCaseDataApi;
        this.jurisdiction = jurisdiction;
        this.caseType = caseType;
        this.eventId = eventId;
    }

    public Long createCase(Map<String, Object> sscsCaseData, String userAuthToken, String serviceAuthToken, String userId) {
        StartEventResponse startEventResponse = coreCaseDataApi.startForCaseworker(
            userAuthToken, serviceAuthToken, userId, jurisdiction, caseType, eventId
        );

        CaseDataContent caseDataContent = caseDataContent(sscsCaseData, startEventResponse.getToken());

        CaseDetails caseDetails = coreCaseDataApi.submitForCaseworker(
            userAuthToken, serviceAuthToken, userId, jurisdiction, caseType, true, caseDataContent
        );

        return caseDetails.getId();
    }

    private CaseDataContent caseDataContent(Map<String, Object> sscsCaseData, String eventToken) {
        return CaseDataContent.builder()
            .data(sscsCaseData)
            .eventToken(eventToken)
            .securityClassification(Classification.PUBLIC)
            .event(Event.builder()
                .description("Case created from exception record")
                .id(eventId)
                .build()
            )
            .build();
    }
}
