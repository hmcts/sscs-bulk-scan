package uk.gov.hmcts.reform.sscs.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilderTest.buildScannedOcrData;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilderTest.buildScannedValidationOcrData;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;

public class SscsKeyValuePairValidatorTest {

    SscsKeyValuePairValidator validator = new SscsKeyValuePairValidator();

    @Test
    public void givenNewFieldsInV2OfTheForm_thenNoErrorsAreGiven() {
        Map<String, Object> pairs = new HashMap<>();
        pairs.put("is_benefit_type_pip", true);
        pairs.put("is_benefit_type_esa", false);
        pairs.put("person1_email", "me@example.com");
        pairs.put("person1_want_sms_notifications", false);
        pairs.put("representative_email", "me@example.com");
        pairs.put("representative_mobile", "07770583222");
        pairs.put("representative_want_sms_notifications", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> scanOcrData = buildScannedOcrData("scanOCRData", pairs.entrySet().stream().map(f -> {
            HashMap<String, Object> valueMap = new HashMap<>();
            valueMap.put("key", f.getKey());
            valueMap.put("value", f.getValue());
            return valueMap;
        }).toArray(HashMap[]::new));

        CaseResponse response = validator.validate(scanOcrData, "scanOCRData");
        assertNull(response.getErrors());
    }


    @Test
    public void givenAValidKeyValuePair_thenReturnAnEmptyCaseResponse() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", "person1_first_name");
        valueMap.put("value", "Bob");

        Map<String, Object> scannedOcrDataMap = buildScannedOcrData("scanOCRData", valueMap);

        CaseResponse response = validator.validate(scannedOcrDataMap, "scanOCRData");
        assertNull(response.getErrors());
    }

    @Test
    public void givenAnInvalidKeyValuePair_thenReturnACaseResponseWithAnError() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", "invalid_key");
        valueMap.put("value", "test");

        Map<String, Object> scannedOcrDataMap = buildScannedOcrData("scanOCRData", valueMap);

        CaseResponse response = validator.validate(scannedOcrDataMap, "scanOCRData");
        assertEquals("#: extraneous key [invalid_key] is not permitted", response.getErrors().get(0));
    }

    @Test
    public void givenMultipleInvalidKeyValuePairs_thenReturnACaseResponseWithMultipleErrors() {

        Map<String, Object> valueMap1 = new HashMap<>();
        Map<String, Object> valueMap2 = new HashMap<>();

        valueMap1.put("key", "invalid_key");
        valueMap1.put("value", "test");
        valueMap2.put("key", "invalid_key2");
        valueMap2.put("value", "test");

        Map<String, Object> scannedOcrDataMap = buildScannedOcrData("scanOCRData", valueMap1, valueMap2);

        CaseResponse response = validator.validate(scannedOcrDataMap, "scanOCRData");
        assertEquals(2, response.getErrors().size());
        assertEquals("#: extraneous key [invalid_key] is not permitted", response.getErrors().get(0));
        assertEquals("#: extraneous key [invalid_key2] is not permitted", response.getErrors().get(1));
    }


    @Test
    public void givenValidationEndpointDataIsValidated_thenNoErrorsAreGiven() {
        Map<String, Object> pairs = new HashMap<>();
        pairs.put("name", "person1_first_name");
        pairs.put("value", "Bob");

        Map<String, Object> scanOcrData = buildScannedValidationOcrData("ocr_data_fields", pairs);

        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new File("result.json"), scanOcrData);
        } catch (Exception e) {
            e.printStackTrace();
        }

        CaseResponse response = validator.validate(scanOcrData, "ocr_data_fields");
        assertNull(response.getErrors());
    }

}
