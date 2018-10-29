package uk.gov.hmcts.reform.sscs.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.domain.ScannedRecord;

@Component
public class SscsJsonExtractor {

    public ScannedData extractJson(Map<String, Object> exceptionCaseData) throws JSONException {
        Map<String, Object> ocrPairs = buildOcrData(exceptionCaseData, "scanOCRData");
        List<ScannedRecord> documents = buildRecordData(exceptionCaseData, "scanRecords");
        return ScannedData.builder().ocrCaseData(ocrPairs).records(documents).build();
    }

    private Map<String, Object> buildOcrData(Map<String, Object> exceptionCaseData, String field) {
        Map<String, Object> pairs = new HashMap<>();

        JSONArray jsonArray = new JSONArray((List) exceptionCaseData.get(field));
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject j = jsonArray.optJSONObject(i);

            JSONObject jsonObject = (JSONObject) j.get("value");

            pairs.put(jsonObject.get("key").toString(), jsonObject.get("value").toString());
        }
        return pairs;
    }

    private List<ScannedRecord> buildRecordData(Map<String, Object> exceptionCaseData, String field) {
        List<ScannedRecord> records = new ArrayList<>();

        JSONArray jsonArray = new JSONArray((List) exceptionCaseData.get(field));
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject j = jsonArray.optJSONObject(i);

            JSONObject jsonObject = (JSONObject) j.get("value");

            records.add(getDeserializeRecord(jsonObject));
        }
        return records;
    }

    public ScannedRecord getDeserializeRecord(JSONObject jsonObject) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        try {
            return mapper.readValue(jsonObject.toString(), ScannedRecord.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
