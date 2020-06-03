package uk.gov.hmcts.reform.sscs.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilderTestOld.buildScannedOcrData;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.InputScannedDoc;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.ccd.domain.DocumentLink;

public class SscsJsonExtractorTestOld {

    @Autowired
    private SscsJsonExtractor sscsJsonExtractor;

    @Before
    public void setup() {
        sscsJsonExtractor = new SscsJsonExtractor();
    }

    LocalDateTime now = LocalDateTime.now();

    @Test
    public void givenExceptionCaseData_thenExtractIntoKeyValuePairs() {

        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", "person1_first_name");
        valueMap.put("value", "Bob");

        Map<String, Object> scannedOcrDataMap = buildScannedOcrData("scanOCRData", valueMap);

        ScannedData result = sscsJsonExtractor.extractJsonOld(scannedOcrDataMap);

        assertEquals("Bob", result.getOcrCaseData().get("person1_first_name"));
    }

    @Test
    public void givenDocumentData_thenExtractIntoSscsDocumentObject() {

        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("fileName", "Test_doc");
        valueMap.put("scannedDate", now.toString());
        valueMap.put("type", "1");
        valueMap.put("subtype", "my subtype");
        valueMap.put("controlNumber", "4");
        JSONObject item = new JSONObject();
        item.put("document_url", "www.test.com");
        valueMap.put("url", item);

        Map<String, Object> scannedOcrDataMap = buildScannedOcrData("scannedDocuments", valueMap);

        ScannedData result = sscsJsonExtractor.extractJsonOld(scannedOcrDataMap);

        InputScannedDoc expectedRecord = InputScannedDoc.builder()
            .type("1").subtype("my subtype").fileName("Test_doc").url(DocumentLink.builder().documentUrl("www.test.com").build()).scannedDate(now).build();

        assertEquals(expectedRecord, result.getRecords().get(0));
    }

    @Test
    public void givenDocumentData_thenExtractIntoSscsDocumentObjectWhenUnknownPropertiesPresent() {

        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("fileName", "Test_doc");
        valueMap.put("scannedDate", now.toString());
        valueMap.put("deliveryDate", "2018-08-10");
        valueMap.put("type", "1");
        valueMap.put("subtype", "my subtype");
        valueMap.put("controlNumber", "4");
        JSONObject item = new JSONObject();
        item.put("document_url", "www.test.com");
        valueMap.put("url", item);

        Map<String, Object> scannedOcrDataMap = buildScannedOcrData("scannedDocuments", valueMap);

        ScannedData result = sscsJsonExtractor.extractJsonOld(scannedOcrDataMap);

        InputScannedDoc expectedRecord = InputScannedDoc.builder()
            .type("1").subtype("my subtype").fileName("Test_doc").url(DocumentLink.builder().documentUrl("www.test.com").build()).scannedDate(now).build();

        assertEquals(expectedRecord, result.getRecords().get(0));
    }

    @Test
    public void givenMultipleDocumentData_thenExtractIntoSscsDocumentObject() {

        Map<String, Object> valueMap1 = new HashMap<>();
        valueMap1.put("fileName", "Test_doc");
        valueMap1.put("scannedDate", now.toString());
        valueMap1.put("type", "1");
        valueMap1.put("subtype", "my subtype1");
        valueMap1.put("controlNumber", "4");
        JSONObject item = new JSONObject();
        item.put("document_url", "www.test.com");
        valueMap1.put("url", item);

        Map<String, Object> valueMap2 = new HashMap<>();
        valueMap2.put("fileName", "Second_test_doc");
        valueMap2.put("scannedDate", now.toString());
        valueMap2.put("type", "2");
        valueMap2.put("subtype", "my subtype2");
        valueMap2.put("controlNumber", "3");
        JSONObject item2 = new JSONObject();
        item2.put("document_url", "www.hello.com");
        valueMap2.put("url", item2);

        Map<String, Object> scannedOcrDataMap = buildScannedOcrData("scannedDocuments", valueMap1, valueMap2);

        ScannedData result = sscsJsonExtractor.extractJsonOld(scannedOcrDataMap);

        InputScannedDoc expectedRecord1 = InputScannedDoc.builder()
            .type("1").subtype("my subtype1").fileName("Test_doc").url(DocumentLink.builder().documentUrl("www.test.com").build()).scannedDate(now).build();

        InputScannedDoc expectedRecord2 = InputScannedDoc.builder()
            .type("2").subtype("my subtype2").fileName("Second_test_doc").url(DocumentLink.builder().documentUrl("www.hello.com").build()).scannedDate(now).build();

        assertEquals(expectedRecord1, result.getRecords().get(0));
        assertEquals(expectedRecord2, result.getRecords().get(1));
    }

    @Test
    public void givenExceptionCaseDataWithEmptyData_thenExtractIntoKeyValuePairs() {

        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("key", "appellant_first_name");
        valueMap.put("value", null);

        Map<String, Object> scannedOcrDataMap = buildScannedOcrData("scanOCRData", valueMap);

        ScannedData result = sscsJsonExtractor.extractJsonOld(scannedOcrDataMap);

        assertNull(result.getOcrCaseData().get("appellant_first_name"));
    }

    @Test
    public void givenExceptionCaseDataWithValidOpeningDate_thenSetCorrectOpeningDate() {

        Map<String, Object> valueMap = new HashMap<>();
        org.joda.time.format.DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd");
        String expectedCreatedDate = dtfOut.print(new DateTime().minusYears(3));

        valueMap.put("openingDate", expectedCreatedDate);

        ScannedData result = sscsJsonExtractor.extractJsonOld(valueMap);

        assertEquals(DateTime.now().minusYears(3).toLocalDate().toString(), result.getOpeningDate());
    }

    @Test
    public void givenExceptionCaseDataWithInvalidOpeningDate_thenSetOpeningDateToToday() {

        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("openingDate", "01-01-99");

        Map<String, Object> scannedOcrDataMap = buildScannedOcrData("scanOCRData", valueMap);

        ScannedData result = sscsJsonExtractor.extractJsonOld(scannedOcrDataMap);

        assertEquals(DateTime.now().toLocalDate().toString(), result.getOpeningDate());
    }
}
