package uk.gov.hmcts.reform.sscs.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilderTest.buildScannedValidationOcrData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.OcrDataField;
import uk.gov.hmcts.reform.sscs.ccd.domain.FormType;

public class SscsKeyValuePairValidatorTest {

    SscsKeyValuePairValidator validator = new SscsKeyValuePairValidator();

    @Test
    public void givenNewFieldsInV2OfTheForm_thenNoErrorsAreGiven() {
        Map<String, Object> pairs = new HashMap<>();
        pairs.put("is_benefit_type_pip", true);
        pairs.put("is_benefit_type_esa", false);
        pairs.put("is_benefit_type_uc", false);
        pairs.put("person1_email", "me@example.com");
        pairs.put("person1_want_sms_notifications", false);
        pairs.put("representative_email", "me@example.com");
        pairs.put("representative_mobile", "07770583222");
        pairs.put("representative_want_sms_notifications", true);

        @SuppressWarnings("unchecked")
        List scanOcrData = buildScannedValidationOcrData(pairs.entrySet().stream().map(f -> {
            HashMap<String, Object> valueMap = new HashMap<>();
            valueMap.put("name", f.getKey());
            valueMap.put("value", f.getValue());
            return valueMap;
        }).toArray(HashMap[]::new));

        @SuppressWarnings("unchecked")
        CaseResponse response = validator.validate(scanOcrData, FormType.SSCS1);
        assertNull(response.getErrors());
    }


    @Test
    public void givenAValidKeyValuePair_thenReturnAnEmptyCaseResponse() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("name", "person1_first_name");
        valueMap.put("value", "Bob");

        List<OcrDataField> scanOcrData = buildScannedValidationOcrData(valueMap);

        CaseResponse response = validator.validate(scanOcrData, FormType.UNKNOWN);
        assertNull(response.getErrors());
    }

    @Test
    public void givenAnInvalidKeyValuePair_thenReturnACaseResponseWithAnError() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("name", "invalid_key");
        valueMap.put("value", "test");

        List<OcrDataField> scanOcrData = buildScannedValidationOcrData(valueMap);

        CaseResponse response = validator.validate(scanOcrData, null);
        assertEquals("#: extraneous key [invalid_key] is not permitted", response.getErrors().get(0));
    }

    @Test
    public void givenMultipleInvalidKeyValuePairs_thenReturnACaseResponseWithMultipleErrors() {

        Map<String, Object> valueMap1 = new HashMap<>();
        Map<String, Object> valueMap2 = new HashMap<>();

        valueMap1.put("name", "invalid_key");
        valueMap1.put("value", "test");
        valueMap2.put("name", "invalid_key2");
        valueMap2.put("value", "test");

        List<OcrDataField> scanOcrData = buildScannedValidationOcrData(valueMap1, valueMap2);

        CaseResponse response = validator.validate(scanOcrData, FormType.SSCS1PE);
        assertEquals(2, response.getErrors().size());
        assertEquals("#: extraneous key [invalid_key] is not permitted", response.getErrors().get(0));
        assertEquals("#: extraneous key [invalid_key2] is not permitted", response.getErrors().get(1));
    }

    @Test
    public void givenValidChildSupportKeyValuePair_thenReturnAnEmptyCaseResponse() {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("name", "person1_child_maintenance_number");
        valueMap.put("value", "Test1234");

        List<OcrDataField> scanOcrData = buildScannedValidationOcrData(valueMap);

        CaseResponse response = validator.validate(scanOcrData, FormType.SSCS2);
        assertNull(response.getErrors());
        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenAValidSscs1FieldsForSscs2_thenReturnValidCaseResponse() {
        Map<String, Object> pairs = new HashMap<>();
        pairs.put("is_benefit_type_pip", true);
        pairs.put("is_benefit_type_esa", false);
        pairs.put("is_benefit_type_uc", false);
        pairs.put("person1_email", "me@example.com");
        pairs.put("person1_want_sms_notifications", false);
        pairs.put("representative_email", "me@example.com");
        pairs.put("representative_mobile", "07770583222");
        pairs.put("representative_want_sms_notifications", true);

        @SuppressWarnings("unchecked")
        List scanOcrData = buildScannedValidationOcrData(pairs.entrySet().stream().map(f -> {
            HashMap<String, Object> valueMap = new HashMap<>();
            valueMap.put("name", f.getKey());
            valueMap.put("value", f.getValue());
            return valueMap;
        }).toArray(HashMap[]::new));

        @SuppressWarnings("unchecked")
        CaseResponse response = validator.validate(scanOcrData, FormType.SSCS2);
        assertNull(response.getErrors());
        assertEquals(0, response.getWarnings().size());
    }
}
