package uk.gov.hmcts.reform.sscs.helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class OcrDataBuilder {

    private static final String VALUE = "value";
    private static final String KEY = "key";

    private OcrDataBuilder() {

    }

    public static Map<String, Object> build(Map<String, Object> exceptionCaseData) {
        Map<String, Object> pairs = new HashMap<>();

        JSONArray jsonArray = new JSONArray((List) exceptionCaseData.get("scanOCRData"));
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject j = jsonArray.optJSONObject(i);

            JSONObject jsonObject = (JSONObject) j.get(VALUE);

            String jsonValue = jsonObject.has(VALUE) && !jsonObject.get(VALUE).equals("") ? jsonObject.get(VALUE).toString() : null;

            if (jsonObject.has(KEY)) {
                pairs.put(jsonObject.get(KEY).toString(), jsonValue);
            }
        }
        return pairs;
    }
}
