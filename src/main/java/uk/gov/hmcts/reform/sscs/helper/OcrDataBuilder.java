package uk.gov.hmcts.reform.sscs.helper;

import io.micrometer.core.instrument.util.StringUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.OcrDataField;

public class OcrDataBuilder {

    private static final String VALUE = "value";
    private static final String KEY = "key";
    private static final String NAME = "name";

    private OcrDataBuilder() {

    }

    //FIXME: Remove this after bulk scan migration
    public static Map<String, Object> buildOld(Map<String, Object> exceptionCaseData, String property) {
        Map<String, Object> pairs = new HashMap<>();

        JSONArray jsonArray = new JSONArray((List) exceptionCaseData.get(property));
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

    public static Map<String, Object> build(List<OcrDataField> exceptionCaseData) {
        Map<String, Object> pairs = new HashMap<>();

        if (exceptionCaseData != null) {
            for (OcrDataField ocrDataField : exceptionCaseData) {
                if (!StringUtils.isEmpty(ocrDataField.getName())) {
                    String value = !StringUtils.isEmpty(ocrDataField.getValue()) ? ocrDataField.getValue() : null;
                    pairs.put(ocrDataField.getName(), value);
                }
            }
        }

        return pairs;
    }
}
