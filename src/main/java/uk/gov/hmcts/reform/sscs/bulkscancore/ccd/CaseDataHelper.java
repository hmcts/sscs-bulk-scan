package uk.gov.hmcts.reform.sscs.bulkscancore.ccd;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.*;

@Component
@Slf4j
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

    //FIXME: Delete after bulk scan migration
    public Long createCase(Map<String, Object> sscsCaseData, String userAuthToken, String serviceAuthToken, String userId, String eventId) {
        log.info("About to create case in helper");
        StartEventResponse startEventResponse = coreCaseDataApi.startForCaseworker(
            userAuthToken, serviceAuthToken, userId, jurisdiction, caseType, eventId
        );

        log.info("Started case event response in helper: " + startEventResponse);
        log.info("Started case event response in helper userAuthToken: " + userAuthToken);
        log.info("Started case event response in helper: serviceAuthToken: " + serviceAuthToken);
        log.info("Started case event response in helper: userId: " + userId);
        log.info("Started case event response in helper: jurisdiction: " + jurisdiction);
        log.info("Started case event response in helper: caseType: " + caseType);
        log.info("Started case event response in helper: eventId: " + eventId);

        CaseDataContent caseDataContent = caseDataContent(sscsCaseData, startEventResponse.getToken(), eventId, "Case created", "Case created from exception record");

        log.info("caseDataContent tostring:" + caseDataContent.toString());

        CaseDetails caseDetails = coreCaseDataApi.submitForCaseworker(
            userAuthToken, serviceAuthToken, userId, jurisdiction, caseType, true, caseDataContent
        );

        return caseDetails.getId();
    }

    //FIXME: Delete after bulk scan migration
    public void updateCase(Map<String, Object> sscsCaseData, String userAuthToken, String serviceAuthToken, String userId, String eventId, Long caseId, String summary, String description) {
        StartEventResponse startEventResponse = coreCaseDataApi.startEventForCaseWorker(
            userAuthToken, serviceAuthToken, userId, jurisdiction, caseType, String.valueOf(caseId), eventId
        );

        CaseDataContent caseDataContent = caseDataContent(sscsCaseData, startEventResponse.getToken(), eventId, summary, description);

        coreCaseDataApi.submitEventForCaseWorker(
            userAuthToken, serviceAuthToken, userId, jurisdiction, caseType, String.valueOf(caseId),true, caseDataContent
        );
    }

    public List<CaseDetails> findCaseBy(Map<String, String> searchCriteria, String userAuthToken, String serviceAuthToken, String userId) {
        return coreCaseDataApi.searchForCaseworker(userAuthToken, serviceAuthToken, userId, jurisdiction, caseType, searchCriteria);
    }

    //FIXME: Delete after bulk scan migration
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
