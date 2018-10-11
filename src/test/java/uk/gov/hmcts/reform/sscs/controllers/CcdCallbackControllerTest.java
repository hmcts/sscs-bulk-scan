package uk.gov.hmcts.reform.sscs.controllers;

import static com.google.common.io.Resources.getResource;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.sscs.domain.bulkscan.CcdCallbackResponse;
import uk.gov.hmcts.reform.sscs.domain.bulkscan.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.service.bulkscan.CcdCallbackHandler;

@RunWith(SpringRunner.class)
@WebMvcTest(CcdCallbackController.class)
public class CcdCallbackControllerTest {

    private static final String TEST_SERVICE_AUTH_TOKEN = "testServiceAuth";
    private static final String TEST_USER_AUTH_TOKEN = "testUserAuth";
    private static final String TEST_USER_ID = "1234";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CcdCallbackHandler ccdCallbackHandler;

    @Test
    public void should_successfully_handle_callback_and_return_exception_record_response() throws Exception {
        given(ccdCallbackHandler.handle(
            any(ExceptionCaseData.class),
            eq(TEST_USER_AUTH_TOKEN), eq(TEST_SERVICE_AUTH_TOKEN),
            eq(TEST_USER_ID))
        ).willReturn(CcdCallbackResponse.builder()
            .data(caseData())
            .build()
        );

        mockMvc.perform(post("/exception-record")
            .contentType(MediaType.APPLICATION_JSON_UTF8)
            .header("Authorization", TEST_USER_AUTH_TOKEN)
            .header("serviceauthorization", TEST_SERVICE_AUTH_TOKEN)
            .header("user-id", TEST_USER_ID)
            .content(exceptionRecord()))
            .andExpect(jsonPath("$['data'].journeyClassification", is("New Application")))
            .andExpect(jsonPath("$['data'].poBoxJurisdiction", is("SSCS")))
            .andExpect(jsonPath("$['data'].poBox", is("SSCSPO")))
            .andExpect(jsonPath("$['data'].openingDate", is("2018-01-11")))

            .andExpect(jsonPath("$['data'].scanRecords[0].value.key", is("firstName")))
            .andExpect(jsonPath("$['data'].scanRecords[0].value.value", is("John")))
            .andExpect(jsonPath("$['data'].scanRecords[0].id", is("d55a7f14-92c3-4134-af78-f2aa2b201841")))

            .andExpect(jsonPath("$['data'].scanRecords[1].value.key", is("lastName")))
            .andExpect(jsonPath("$['data'].scanRecords[1].value.value", is("Smith")))
            .andExpect(jsonPath("$['data'].scanRecords[1].id", is("d55a7f14-92c3-4134-af78-f2aa2b201841")));
    }

    @Test
    public void should_throw_exception_when_handler_fails() throws Exception {
        given(ccdCallbackHandler.handle(
            any(ExceptionCaseData.class),
            eq(TEST_USER_AUTH_TOKEN), eq(TEST_SERVICE_AUTH_TOKEN),
            eq(TEST_USER_ID))
        ).willThrow(RuntimeException.class);

        mockMvc.perform(post("/exception-record")
            .contentType(MediaType.APPLICATION_JSON_UTF8)
            .header("Authorization", TEST_USER_AUTH_TOKEN)
            .header("serviceauthorization", TEST_SERVICE_AUTH_TOKEN)
            .header("user-id", TEST_USER_ID)
            .content(exceptionRecord()))
            .andExpect(status().is5xxServerError());
    }


    private String exceptionRecord() throws IOException {
        URL url = getResource("exceptionrecord.json");
        return Resources.toString(url, Charsets.toCharset("UTF-8"));
    }

    private Map<String, Object> caseData() {
        return ImmutableMap.of(
            "journeyClassification", "New Application",
            "poBoxJurisdiction", "SSCS",
            "poBox", "SSCSPO",
            "openingDate", "2018-01-11",
            "scanRecords", ocrData()
        );
    }

    private List<ScannedOcrData> ocrData() {
        ScannedOcrData ocrData1 = new ScannedOcrData();
        ocrData1.setValue(ImmutableMap.of("key", "firstName", "value", "John"));
        ocrData1.setId("d55a7f14-92c3-4134-af78-f2aa2b201841");

        ScannedOcrData ocrData2 = new ScannedOcrData();
        ocrData2.setValue(ImmutableMap.of("key", "lastName", "value", "Smith"));
        ocrData2.setId("d55a7f14-92c3-4134-af78-f2aa2b201841");

        List<ScannedOcrData> ocrDataArray = new ArrayList<>();
        ocrDataArray.add(ocrData1);
        ocrDataArray.add(ocrData2);

        return ocrDataArray;
    }

    // This class is required as jackson cannot deserialize JsonObject if we use it to create scanned ocr data
    public class ScannedOcrData {
        private Map<String, Object> value;
        private String id;

        public Map<String, Object> getValue() {
            return value;
        }

        private void setValue(Map<String, Object> value) {
            this.value = value;
        }

        public String getId() {
            return id;
        }

        private void setId(String id) {
            this.id = id;
        }
    }
}
