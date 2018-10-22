package uk.gov.hmcts.reform.sscs.json;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
public class SscsJsonExtractor {

    public HashMap<String, Object> extractJson(Map<String, Object> exceptionCaseData) {
        HashMap<String, Object> pairs = new HashMap<>();

        try {
            JSONArray jsonArray = new JSONArray(exceptionCaseData.get("scanOCRData").toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject j = jsonArray.optJSONObject(i);

                JSONObject jsonObject = (JSONObject) j.get("value");

                pairs.put(jsonObject.get("key").toString(), jsonObject.get("value").toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return pairs;
    }
}
