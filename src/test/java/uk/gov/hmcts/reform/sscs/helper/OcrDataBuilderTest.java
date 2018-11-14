package uk.gov.hmcts.reform.sscs.helper;

import static org.junit.Assert.*;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilder.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class OcrDataBuilderTest {

    @Test
    public void givenExceptionData_thenConvertIntoKeyValuePairs() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", "person1_first_name");
        valueMap.put("value", "Bob");

        Map<String, Object> result = build(buildScannedOcrData("scanOCRData", valueMap));

        assertEquals("Bob", result.get("person1_first_name"));
    }

    @Test
    public void givenExceptionDataWithNullValue_thenConvertIntoKeyValuePairs() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", "person1_first_name");
        valueMap.put("value", null);

        Map<String, Object> result = build(buildScannedOcrData("scanOCRData", valueMap));

        assertNull(result.get("person1_first_name"));
    }

    @Test
    public void givenExceptionDataWithNullKeyAndNullValue_thenConvertIntoKeyValuePairs() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", null);
        valueMap.put("value", null);

        Map<String, Object> result = build(buildScannedOcrData("scanOCRData", valueMap));

        assertEquals(0, result.size());
    }

    @SafeVarargs
    public static final Map<String, Object> buildScannedOcrData(String key, Map<String, Object>... valueMap) {
        Map<String, Object> scannedOcrDataMap = new HashMap<>();

        List<Object> ocrList = new ArrayList<>();

        for (Map<String, Object> values: valueMap) {
            Map<String, Object> ocrValuesMap = new HashMap<>();
            ocrValuesMap.put("value", values);
            ocrList.add(ocrValuesMap);
        }

        scannedOcrDataMap.put(key, ocrList);

        return scannedOcrDataMap;
    }

}
