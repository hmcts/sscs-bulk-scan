package uk.gov.hmcts.reform.sscs.service;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.Map;

@Component
public class CaseDataHelper {

    private final CoreCaseDataApi coreCaseDataApi;

    public CaseDataHelper(CoreCaseDataApi coreCaseDataApi) {
        this.coreCaseDataApi = coreCaseDataApi;
    }

    public Long createCase(Map<String, Object> sscsCaseCaseData, String userAuthorisationToken, String serviceAuthorisationToken, String userId) {
        //String userToken="Bearer eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJkZDVmaG5jdXZ2ZDBjMXBkNWJna2RiY2xlYiIsInN1YiI6IjI1IiwiaWF0IjoxNTM4NzM0MzY2LCJleHAiOjE1Mzg3NTIzNjYsImRhdGEiOiJjYXNld29ya2VyLXNzY3Mtc3lzdGVtdXBkYXRlLGNhc2V3b3JrZXItc3Njcy1zeXN0ZW11cGRhdGUtbG9hMCIsInR5cGUiOiJBQ0NFU1MiLCJpZCI6IjI1IiwiZm9yZW5hbWUiOiJJbnRlZ3JhdGlvbiIsInN1cm5hbWUiOiJUZXN0IiwiZGVmYXVsdC1zZXJ2aWNlIjoiUHJvYmF0ZSIsImxvYSI6MCwiZGVmYXVsdC11cmwiOiJodHRwczovL2xvY2FsaG9zdDo5MDAwL3BvYy9wcm9iYXRlIiwiZ3JvdXAiOiJwcm9iYXRlLXByaXZhdGUtYmV0YSJ9._DY6kX3MPX_WdCle9UlNt80nb3bqUNnmrkGcMK1oZGo";
        //String serviceToken = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJjY2RfZ3ciLCJleHAiOjE1MzkwMDYyMjJ9.RxKpFkjeKw-13jq-fLuDOBlqd85sC8mjzrLtOdii-kC5pa42-iFTbCaH3nQV6IHTVvR4AFULQ3XHs7L4jId1qg";
        try {
            StartEventResponse startEventResponse = coreCaseDataApi.startForCaseworker(
                userAuthorisationToken, serviceAuthorisationToken, userId, "SSCS", "Benefit", "appealCreated"
            );

            CaseDataContent caseDataContent = CaseDataContent.builder()
                .data(sscsCaseCaseData)
                .eventToken(startEventResponse.getToken())
                .event(Event.builder()
                    .description("Case created from exception record")
                    .id("appealCreated")
                    .build()
                )
                .build();

            CaseDetails caseDetails = coreCaseDataApi.submitForCaseworker(
                userAuthorisationToken, serviceAuthorisationToken, userId, "SSCS", "Benefit", true, caseDataContent
            );

            return caseDetails.getId();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
