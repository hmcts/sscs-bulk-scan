package uk.gov.hmcts.reform.sscs.json;

import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilder.build;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedRecord;
import uk.gov.hmcts.reform.sscs.exceptions.BulkScanJsonException;

@Component
public class SscsJsonExtractor {

    public ScannedData extractJson(Map<String, Object> exceptionCaseData) {
        Map<String, Object> ocrPairs = build(exceptionCaseData, "scanOCRData");
        List<ScannedRecord> documents = buildScannedDocumentData(exceptionCaseData, "scannedDocuments");
        return ScannedData.builder().ocrCaseData(ocrPairs).records(documents).build();
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
}
