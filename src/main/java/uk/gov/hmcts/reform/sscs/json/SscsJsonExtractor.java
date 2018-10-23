package uk.gov.hmcts.reform.sscs.json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
public class SscsJsonExtractor {

    public Map<String, Object> extractJson(Map<String, Object> exceptionCaseData) throws JSONException {
        Map<String, Object> pairs = new HashMap<>();

        JSONArray jsonArray = new JSONArray((List) exceptionCaseData.get("scanOCRData"));
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject j = jsonArray.optJSONObject(i);

            JSONObject jsonObject = (JSONObject) j.get("value");

            pairs.put(jsonObject.get("key").toString(), jsonObject.get("value").toString());
        }
        return pairs;
    }
}
