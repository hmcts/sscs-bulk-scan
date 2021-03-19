package uk.gov.hmcts.reform.sscs.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.io.IOException;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(locations = "classpath:application_e2e.yaml")
@RunWith(JUnitParamsRunner.class)
@Slf4j
public class TransformationFunctionalTest extends BaseFunctionalTest {

    @Ignore
    @Test
    public void transform_appeal_created_case_when_all_fields_entered() throws IOException {
        String expectedJson = getJson("exception/output/expected_all_fields_entered.json");

        String person1Nino = generateRandomNino();
        String person2Nino = generateRandomNino();

        expectedJson = replaceNino(expectedJson, person1Nino, person2Nino);

        String jsonRequest = getJson("exception/all_fields_entered.json");
        jsonRequest = replaceNino(jsonRequest, person1Nino, person2Nino);

        verifyResponseIsExpected(expectedJson, transformExceptionRequest(jsonRequest, OK.value()));
    }

    @Ignore
    @Test
    public void transform_appeal_created_case_when_all_fields_entered_uc() throws IOException {
        String expectedJson = getJson("exception/output/expected_all_fields_entered_uc.json");

        String person1Nino = generateRandomNino();
        String person2Nino = generateRandomNino();

        expectedJson = replaceNino(expectedJson, person1Nino, person2Nino);

        String jsonRequest = getJson("exception/all_fields_entered_uc.json");
        jsonRequest = replaceNino(jsonRequest, person1Nino, person2Nino);

        verifyResponseIsExpected(expectedJson, transformExceptionRequest(jsonRequest, OK.value()));
    }

    //FIXME nino
    @Ignore
    @Test
    public void transform_incomplete_case_when_missing_mandatory_fields() throws IOException {
        String expectedJson = getJson("exception/output/expected_some_mandatory_fields_missing.json");

        String jsonRequest = getJson("exception/some_mandatory_fields_missing.json");

        verifyResponseIsExpected(expectedJson, transformExceptionRequest(jsonRequest, OK.value()));
    }

    @Ignore
    @Test
    @Parameters({"see scanned SSCS1 form,mrn_date_greater_than_13_months.json", ",mrn_date_greater_than_13_months_grounds_missing.json"})
    public void transform_interlocutory_review_case_when_mrn_date_greater_than_13_months(String appealGrounds,
                                                                                         String path) throws IOException {
        String expectedJson = getJson("exception/output/expected_" + path);

        String person1Nino = generateRandomNino();
        String person2Nino = generateRandomNino();
        expectedJson = replaceNino(expectedJson, person1Nino, person2Nino);

        String jsonRequest = getJson("exception/mrn_date_greater_than_13_months.json");
        jsonRequest = jsonRequest.replace("APPEAL_GROUNDS", appealGrounds);
        jsonRequest = replaceNino(jsonRequest, person1Nino, person2Nino);

        verifyResponseIsExpected(expectedJson, transformExceptionRequest(jsonRequest, OK.value()));
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

    @Ignore
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
