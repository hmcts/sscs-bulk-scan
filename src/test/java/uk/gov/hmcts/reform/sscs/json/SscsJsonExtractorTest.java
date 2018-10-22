package uk.gov.hmcts.reform.sscs.json;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SscsJsonExtractorTest {

    @Autowired
    private SscsJsonExtractor sscsJsonExtractor;

    @Before
    public void setup() {
        sscsJsonExtractor = new SscsJsonExtractor();
    }

    @Test
    public void givenExceptionCaseData_thenExtractIntoKeyValuePairs() {

        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", "appellant_first_name");
        valueMap.put("value", "Bob");

        Map<String, Object> ocrValuesMap = new HashMap<>();
        ocrValuesMap.put("value", valueMap);

        List<Object> ocrList = new ArrayList<>();
        ocrList.add(ocrValuesMap);

        Map<String, Object> scannedOcrDataMap = new HashMap<>();
        scannedOcrDataMap.put("scanOCRData", ocrList);

        HashMap<String, Object> result = sscsJsonExtractor.extractJson(scannedOcrDataMap);

        assertEquals("Bob", result.get("appellant_first_name"));
    }

}
