package uk.gov.hmcts.reform.sscs.helper;

import static org.junit.Assert.*;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilder.buildOld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.OcrDataField;

//FIXME: Remove after bulk scan migration
public class OcrDataBuilderTestOld {

    @Test
    public void givenExceptionData_thenConvertIntoKeyValuePairs() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", "person1_first_name");
        valueMap.put("value", "Bob");

        Map<String, Object> result = buildOld(buildScannedOcrData("scanOCRData", valueMap), "scanOCRData");

        assertEquals("Bob", result.get("person1_first_name"));
    }

    @Test
    public void givenExceptionDataWithNullValue_thenConvertIntoKeyValuePairs() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", "person1_first_name");
        valueMap.put("value", null);

        Map<String, Object> result = buildOld(buildScannedOcrData("scanOCRData", valueMap), "scanOCRData");

        assertNull(result.get("person1_first_name"));
    }

    @Test
    public void givenExceptionDataWithEmptyStringValue_thenConvertIntoKeyValuePairsWithNullValue() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", "person1_first_name");
        valueMap.put("value", "");

        Map<String, Object> result = buildOld(buildScannedOcrData("scanOCRData", valueMap), "scanOCRData");

        assertNull(result.get("person1_first_name"));
    }

    @Test
    public void givenExceptionDataWithNullKeyAndNullValue_thenConvertIntoKeyValuePairs() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", null);
        valueMap.put("value", null);

        Map<String, Object> result = buildOld(buildScannedOcrData("scanOCRData", valueMap), "scanOCRData");

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

    @SafeVarargs
    public static final List<OcrDataField> buildScannedValidationOcrData(Map<String, Object>... valueMap) {
        List<OcrDataField> scannedOcrDataList = new ArrayList<>();

        for (Map<String, Object> values: valueMap) {
            String name = values.get("name") != null ? values.get("name").toString() : null;
            String value = values.get("value") != null ? values.get("value").toString() : null;
            scannedOcrDataList.add(new OcrDataField(name, value));
        }

        return scannedOcrDataList;
    }

    @SafeVarargs
    public static final ExceptionRecord buildExceptionRecordFromOcrData(Map<String, Object>... valueMap) {
        return ExceptionRecord.builder().ocrDataFields(buildScannedValidationOcrData(valueMap)).build();
    }
}
