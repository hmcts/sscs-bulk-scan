package uk.gov.hmcts.reform.sscs.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;

import io.restassured.response.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;

@SpringBootTest
@TestPropertySource(locations = "classpath:application_e2e.yaml")
@RunWith(JUnitParamsRunner.class)
@Slf4j
public class SscsBulkScanFunctionalTest extends BaseFunctionalTest {

    @Value("${document_management.url}")
    private String documentManagementUrl;

    @Test
    public void create_appeal_created_case_when_all_fields_entered() throws IOException {
        String json = getJson("import/all_fields_entered.json");
        json = replaceNino(json);

        json = json.replace("{DM_STORE}", documentManagementUrl);

        Response response = exceptionRecordEndpointRequest(json, OK.value());

        //Due to the async service bus hitting the evidence share service, we can't be sure what the state will be...
        List<String> possibleStates = new ArrayList<>(Arrays.asList("validAppeal", "withDwp"));

        SscsCaseDetails caseInCcd = findCaseInCcd(response);
        log.info("Functional test(create_appeal_created_case_when_all_fields_entered), ccdId: {}, state: {}",
            caseInCcd.getData().getCcdCaseId(), caseInCcd.getState());
        assertTrue(possibleStates.contains(caseInCcd.getState()));
    }

    @Test
    public void create_valid_appeal_when_all_fields_entered_for_uc_case() throws IOException {
        String json = getJson("import/all_fields_entered_uc.json");
        json = replaceNino(json);

        json = json.replace("{DM_STORE}", documentManagementUrl);

        Response response = exceptionRecordEndpointRequest(json, OK.value());

        //Due to the async service bus hitting the evidence share service, we can't be sure what the state will be...
        List<String> possibleStates = new ArrayList<>(Arrays.asList("validAppeal", "withDwp"));

        SscsCaseDetails caseInCcd = findCaseInCcd(response);
        log.info("Functional test(create_appeal_created_case_when_all_fields_entered), ccdId: {}, state: {}",
            caseInCcd.getData().getCcdCaseId(), caseInCcd.getState());
        assertTrue(possibleStates.contains(caseInCcd.getState()));
    }

    @Test
    public void create_incomplete_case_when_missing_mandatory_fields() throws IOException {
        String json = getJson("import/some_mandatory_fields_missing.json");
        json = replaceNino(json);
        Response response = exceptionRecordEndpointRequest(json, OK.value());

        assertEquals("incompleteApplication", findCaseInCcd(response).getState());
    }

    @Test
    @Parameters({"see scanned SSCS1 form,over13months", ",over13MonthsAndGroundsMissing"})
    public void create_interlocutory_review_case_when_mrn_date_greater_than_13_months(String appealGrounds,
                                                                                      String expected) throws IOException {
        String json = getJson("import/mrn_date_greater_than_13_months.json");
        json = json.replace("APPEAL_GROUNDS", appealGrounds);
        json = replaceNino(json);
        Response response = exceptionRecordEndpointRequest(json, OK.value());

        SscsCaseDetails caseInCcd = findCaseInCcd(response);
        assertEquals("interlocutoryReviewState", caseInCcd.getState());
        assertEquals(expected, caseInCcd.getData().getInterlocReferralReason());
    }

    @Test
    public void validate_nino_normalised() throws IOException {
        createCase();
        String json = getJson("import/validate_appeal_created_case_request.json");
        json = json.replace("CASE_ID_TO_BE_REPLACED", ccdCaseId);

        validateRecordEndpointRequest(json, OK.value());

        SscsCaseDetails caseDetails = ccdService.getByCaseId(Long.valueOf(ccdCaseId), idamTokens);

        assertEquals("AB225566B", caseDetails.getData().getAppeal().getAppellant().getIdentity().getNino());
    }

    @Test
    public void validate_and_update_incomplete_case_to_appeal_created_case() throws IOException {
        createCase();
        String json = getJson("import/validate_appeal_created_case_request.json");
        json = json.replace("CASE_ID_TO_BE_REPLACED", ccdCaseId);

        validateRecordEndpointRequest(json, OK.value());

        SscsCaseDetails caseDetails = ccdService.getByCaseId(Long.valueOf(ccdCaseId), idamTokens);

        assertEquals("appealCreated", caseDetails.getState());
    }
}
