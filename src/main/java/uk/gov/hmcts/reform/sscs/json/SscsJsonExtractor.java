package uk.gov.hmcts.reform.sscs.json;

import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilder.build;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilder.buildOld;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.InputScannedDoc;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedRecord;
import uk.gov.hmcts.reform.sscs.exceptions.BulkScanJsonException;

@Component
public class SscsJsonExtractor {

    private static DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    //FIXME: Remove after bulk scan migration
    public ScannedData extractJsonOld(Map<String, Object> exceptionCaseData) {
        Map<String, Object> ocrPairs = buildOld(exceptionCaseData, "scanOCRData");
        List<ScannedRecord> documents = buildScannedDocumentData(exceptionCaseData, "scannedDocuments");
        String openingDate = extractOpeningDate(exceptionCaseData);

        List<InputScannedDoc> convertedDocs = convertScannedDocsToNewType(documents);

        return ScannedData.builder().ocrCaseData(ocrPairs).records(convertedDocs).openingDate(openingDate).build();
    }

    public ScannedData extractJson(ExceptionRecord exceptionCaseData) {
        Map<String, Object> ocrPairs = build(exceptionCaseData.getOcrDataFields());
        String openingDate = exceptionCaseData.getOpeningDate() != null ? exceptionCaseData.getOpeningDate().toLocalDate().toString() : LocalDate.now().toString();
        return ScannedData.builder().ocrCaseData(ocrPairs).records(exceptionCaseData.getScannedDocuments()).openingDate(openingDate).build();
    }

    private List<InputScannedDoc> convertScannedDocsToNewType(List<ScannedRecord> documents) {

        List<InputScannedDoc> inputScannedDocs = new ArrayList<>();
        for (ScannedRecord record : documents) {
            inputScannedDocs.add(new InputScannedDoc(record.getType(), record.getSubtype(), record.getUrl(), record.getControlNumber(), record.getFileName(),
                LocalDateTime.parse(record.getScannedDate()), null));
        }
        return inputScannedDocs;
    }

    private List<ScannedRecord> buildScannedDocumentData(Map<String, Object> exceptionCaseData, String field) {
        List<ScannedRecord> records = new ArrayList<>();

        JSONArray jsonArray = new JSONArray((List) exceptionCaseData.get(field));
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject j = jsonArray.optJSONObject(i);

            JSONObject jsonObject = (JSONObject) j.get("value");

            records.add(getDeserializeRecord(jsonObject));
        }
        return records;
    }

    public ScannedRecord getDeserializeRecord(JSONObject jsonObject)  {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        try {
            return mapper.readValue(jsonObject.toString(), ScannedRecord.class);
        } catch (IOException e) {
            throw new BulkScanJsonException(e);
        }
    }

    private String extractOpeningDate(Map<String, Object> caseData) {
        String openingDate = (String) caseData.get("openingDate");
        return (StringUtils.isEmpty(openingDate) || openingDate.length() < 10)
            ? LocalDate.now().toString()
            : LocalDate.parse(openingDate.substring(0, 10), DATE_FORMAT).toString();
    }
}
