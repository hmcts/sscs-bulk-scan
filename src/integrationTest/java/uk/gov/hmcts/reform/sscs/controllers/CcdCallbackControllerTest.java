package uk.gov.hmcts.reform.sscs.controllers;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sscs.auth.AuthService;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CcdCallbackControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int randomServerPort;

    @MockBean
    private AuthService authService;

    @Test
    public void should_successfully_handle_callback_and_return_exception_record_response() throws Exception {

        when(authService.authenticate("testServiceAuthToken")).thenReturn("testService");
        doNothing().when(authService).assertIsAllowedToHandleCallback("testService");

        final String baseUrl = "http://localhost:" + randomServerPort + "/exception-record/";

        URI uri = new URI(baseUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "testUserAuthToken");
        headers.set("serviceauthorization", "testServiceAuthToken");
        headers.set("user-id", "1");

        ExceptionCaseData exceptionCaseData = ExceptionCaseData.builder()
            .caseDetails(CaseDetails.builder()
                .caseData(caseData())
                .build())
            .build();

        HttpEntity<ExceptionCaseData> request = new HttpEntity<>(exceptionCaseData, headers);

        ResponseEntity<String> result = this.restTemplate.postForEntity(uri, request, String.class);

        //Verify request succeed
        Assertions.assertThat(result.getStatusCodeValue()).isEqualTo(200);

    }

    private Map<String, Object> caseData() {
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("have_right_to_appeal_yes", true);
        caseData.put("have_right_to_appeal_no", false);
        caseData.put("contains_mrn", true);
        caseData.put("benefit_type_description", "Employment Support Allowance");
        caseData.put("person1_title", "Mr");
        caseData.put("person1_last_name", "Smith");
        caseData.put("person1_address_line1", "test_add_line1");
        caseData.put("person1_address_line2", "test_add_line2");
        caseData.put("representative_name", "ABC Advisory Services");
        caseData.put("representative_address_line1", "63 test");
        caseData.put("representative_address_line2", "The Square");
        caseData.put("representative_address_line3", "test");
        caseData.put("representative_address_line4", "Surrey");
        caseData.put("representative_postcode", "RH1 6RE");
        caseData.put("representative_phone_number", "012345678");
        caseData.put("representative_person_title", "Mr");
        caseData.put("representative_person_firstname", "Peter");
        caseData.put("representative_person_lastname", "Hyland");
        caseData.put("appeal_grounds", "see scanned SSCS1 form");
        caseData.put("appeal_late_reason", "see scanned SSCS1 form");
        caseData.put("is_hearing_type_oral", true);
        caseData.put("is_hearing_type_paper", false);
        caseData.put("hearing_options_exclude_dates", "I cannot attend on Mondays and I am on holiday in October.");
        caseData.put("hearing_support_arrangements", "Wheelchair access");
        caseData.put("hearing_support_language", "French");
        caseData.put("hearing_options_dialect", "");
        caseData.put("agree_less_hearing_notice_yes", true);
        caseData.put("agree_less_hearing_notice_no", false);
        caseData.put("signature_person1_name", "Sarah Smith");
        caseData.put("signature_appeal_date", "01/04/2018");

        return caseData;
    }
}
