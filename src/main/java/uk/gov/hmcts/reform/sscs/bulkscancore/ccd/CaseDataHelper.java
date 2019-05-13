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

    public CaseDataHelper(
        CoreCaseDataApi coreCaseDataApi,
        @Value("${ccd.case.jurisdiction}") String jurisdiction,
        @Value("${ccd.case.type}") String caseType
    ) {
        this.coreCaseDataApi = coreCaseDataApi;
        this.jurisdiction = jurisdiction;
        this.caseType = caseType;
    }

    public Long createCase(Map<String, Object> sscsCaseData, String userAuthToken, String serviceAuthToken, String userId, String eventId) {
        StartEventResponse startEventResponse = coreCaseDataApi.startForCaseworker(
            userAuthToken, serviceAuthToken, userId, jurisdiction, caseType, eventId
        );

        CaseDataContent caseDataContent = caseDataContent(sscsCaseData, startEventResponse.getToken(), eventId, "Case created", "Case created from exception record");

        CaseDetails caseDetails = coreCaseDataApi.submitForCaseworker(
            userAuthToken, serviceAuthToken, userId, jurisdiction, caseType, true, caseDataContent
        );

        return caseDetails.getId();
    }

    public void updateCase(Map<String, Object> sscsCaseData, String userAuthToken, String serviceAuthToken, String userId, String eventId, Long caseId, String description) {
        StartEventResponse startEventResponse = coreCaseDataApi.startEventForCaseWorker(
            userAuthToken, serviceAuthToken, userId, jurisdiction, caseType, String.valueOf(caseId), eventId
        );

        CaseDataContent caseDataContent = caseDataContent(sscsCaseData, startEventResponse.getToken(), eventId, "Case updated", description);

        coreCaseDataApi.submitEventForCaseWorker(
            userAuthToken, serviceAuthToken, userId, jurisdiction, caseType, String.valueOf(caseId),true, caseDataContent
        );
    }

    private CaseDataContent caseDataContent(Map<String, Object> sscsCaseData, String eventToken, String eventId, String summary, String description) {
        return CaseDataContent.builder()
            .data(sscsCaseData)
            .eventToken(eventToken)
            .securityClassification(Classification.PUBLIC)
            .event(Event.builder()
                .summary(summary)
                .description(description)
                .id(eventId)
                .build()
            )
            .build();
    }
}
