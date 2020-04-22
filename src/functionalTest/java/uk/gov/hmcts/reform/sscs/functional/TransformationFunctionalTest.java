package uk.gov.hmcts.reform.sscs.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.Assert.assertEquals;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.io.IOException;
import java.util.Map;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(locations = "classpath:application_e2e.yaml")
@RunWith(JUnitParamsRunner.class)
@Slf4j
public class TransformationFunctionalTest extends BaseFunctionalTest {

    @SuppressWarnings("unchecked")
    @Test
    public void transform_appeal_created_case_when_all_fields_entered() throws IOException {
        String json = getJson("exception/all_fields_entered.json");
        Response response = transformExceptionRequest(json, OK.value());

        JsonPath transformationResponse = response.getBody().jsonPath();

        assertSoftly(softly -> {
            softly.assertThat(transformationResponse.getList("warnings")).isEmpty();
            softly.assertThat(transformationResponse.getMap("case_creation_details").get("case_type_id"))
                .isEqualTo("Benefit");
            softly.assertThat(transformationResponse.getMap("case_creation_details").get("event_id"))
                .isEqualTo("validAppealCreated");

            Map<String, Object> caseData = (Map<String, Object>) transformationResponse
                .getMap("case_creation_details")
                .get("case_data");

            softly.assertThat(caseData.get("caseCreated")).isEqualTo("2019-08-02");

            softly.assertAll();
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void transform_incomplete_case_when_missing_mandatory_fields() throws IOException {
        String json = getJson("exception/some_mandatory_fields_missing.json");
        Response response = transformExceptionRequest(json, OK.value());

        JsonPath transformationResponse = response.getBody().jsonPath();

        assertSoftly(softly -> {
            softly.assertThat(transformationResponse.getList("warnings")).isNotEmpty();
            softly.assertThat(transformationResponse.getMap("case_creation_details").get("case_type_id"))
                .isEqualTo("Benefit");
            softly.assertThat(transformationResponse.getMap("case_creation_details").get("event_id"))
                .isEqualTo("incompleteApplicationReceived");

            Map<String, Object> caseData = (Map<String, Object>) transformationResponse
                .getMap("case_creation_details")
                .get("case_data");

            softly.assertThat(caseData.get("caseCreated")).isEqualTo("2019-08-02");

            softly.assertAll();
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    @Parameters({"see scanned SSCS1 form,over13months", ",over13MonthsAndGroundsMissing"})
    public void transform_interlocutory_review_case_when_mrn_date_greater_than_13_months(String appealGrounds,
                                                                                      String expected) throws IOException {
        String json = getJson("exception/mrn_date_greater_than_13_months.json");
        json = json.replace("APPEAL_GROUNDS", appealGrounds);

        Response response = transformExceptionRequest(json, OK.value());

        JsonPath transformationResponse = response.getBody().jsonPath();

        assertSoftly(softly -> {
            softly.assertThat(transformationResponse.getList("warnings")).isEmpty();
            softly.assertThat(transformationResponse.getMap("case_creation_details").get("case_type_id"))
                .isEqualTo("Benefit");
            softly.assertThat(transformationResponse.getMap("case_creation_details").get("event_id"))
                .isEqualTo("nonCompliant");

            Map<String, Object> caseData = (Map<String, Object>) transformationResponse
                .getMap("case_creation_details")
                .get("case_data");

            softly.assertThat(caseData.get("caseCreated")).isEqualTo("2019-08-02");
            assertEquals(expected, caseData.get("interlocReferralReason"));

            softly.assertAll();
        });
    }

    @Test
    public void should_not_transform_exception_record_when_schema_validation_fails_and_respond_with_422() throws IOException {
        String json = getJson("exception/invalid_name_key.json");

        Response response = transformExceptionRequest(json, UNPROCESSABLE_ENTITY.value());

        assertThat(response.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY.value());

        JsonPath errorResponse = response.getBody().jsonPath();

        assertThat(errorResponse.getList("errors"))
            .hasSize(1)
            .containsOnly("#: extraneous key [first_name] is not permitted");
        assertThat(errorResponse.getList("warnings")).isEmpty();
        assertThat(errorResponse.getMap("")).containsOnlyKeys("errors", "warnings");
    }

    @Test
    public void should_not_transform_exception_record_when_validation_error_and_respond_with_422() throws IOException {
        String json = getJson("exception/invalid_mobile_number.json");

        Response response = transformExceptionRequest(json, UNPROCESSABLE_ENTITY.value());

        assertThat(response.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY.value());

        JsonPath errorResponse = response.getBody().jsonPath();

        assertThat(errorResponse.getList("errors"))
            .hasSize(1)
            .containsOnly("person1_mobile is invalid");
        assertThat(errorResponse.getList("warnings")).isEmpty();
        assertThat(errorResponse.getMap("")).containsOnlyKeys("errors", "warnings");
    }
}
