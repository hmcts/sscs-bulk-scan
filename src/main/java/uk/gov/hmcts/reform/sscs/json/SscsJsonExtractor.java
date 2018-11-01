package uk.gov.hmcts.reform.sscs.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final String VALUE = "value";
    private static final String KEY = "key";

    public ScannedData extractJson(Map<String, Object> exceptionCaseData) {
        Map<String, Object> ocrPairs = buildOcrData(exceptionCaseData, "scanOCRData");
        List<ScannedRecord> documents = buildScannedDocumentData(exceptionCaseData, "scanRecords");
        return ScannedData.builder().ocrCaseData(ocrPairs).records(documents).build();
    }

    private Map<String, Object> buildOcrData(Map<String, Object> exceptionCaseData, String field) {
        Map<String, Object> pairs = new HashMap<>();

        JSONArray jsonArray = new JSONArray((List) exceptionCaseData.get(field));
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject j = jsonArray.optJSONObject(i);

            JSONObject jsonObject = (JSONObject) j.get(VALUE);

            pairs.put(jsonObject.get(KEY).toString(), jsonObject.get(VALUE).toString());
        }
        return pairs;
    }

    private List<ScannedRecord> buildScannedDocumentData(Map<String, Object> exceptionCaseData, String field) {
        List<ScannedRecord> records = new ArrayList<>();

        JSONArray jsonArray = new JSONArray((List) exceptionCaseData.get(field));
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject j = jsonArray.optJSONObject(i);

            JSONObject jsonObject = (JSONObject) j.get(VALUE);

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
