package uk.gov.hmcts.reform.sscs.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilderTest.buildScannedOcrData;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;

public class SscsKeyValuePairValidatorTest {

    SscsKeyValuePairValidator validator = new SscsKeyValuePairValidator("/schema/sscs-bulk-scan-schema.json");

    @Test
    public void givenAValidKeyValuePair_thenReturnAnEmptyCaseResponse() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", "person1_first_name");
        valueMap.put("value", "Bob");

        Map<String, Object> scannedOcrDataMap = buildScannedOcrData("scanOCRData", valueMap);

        CaseResponse response = validator.validate(scannedOcrDataMap);
        assertNull(response.getErrors());
    }

    @Test
    public void givenAnInvalidKeyValuePair_thenReturnACaseResponseWithAnError() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", "invalid_key");
        valueMap.put("value", "test");

        Map<String, Object> scannedOcrDataMap = buildScannedOcrData("scanOCRData", valueMap);

        CaseResponse response = validator.validate(scannedOcrDataMap);
        assertEquals("#: extraneous key [invalid_key] is not permitted", response.getErrors().get(0));
    }

    @Test
    public void givenMulitpleInvalidKeyValuePairs_thenReturnACaseResponseWithMultipleErrors() {

        Map<String, Object> valueMap1 = new HashMap<>();
        Map<String, Object> valueMap2 = new HashMap<>();

        valueMap1.put("key", "invalid_key");
        valueMap1.put("value", "test");
        valueMap2.put("key", "invalid_key2");
        valueMap2.put("value", "test");

        Map<String, Object> scannedOcrDataMap = buildScannedOcrData("scanOCRData", valueMap1, valueMap2);

        CaseResponse response = validator.validate(scannedOcrDataMap);
        assertEquals(2, response.getErrors().size());
        assertEquals("#: extraneous key [invalid_key] is not permitted", response.getErrors().get(0));
        assertEquals("#: extraneous key [invalid_key2] is not permitted", response.getErrors().get(1));
    }


}
