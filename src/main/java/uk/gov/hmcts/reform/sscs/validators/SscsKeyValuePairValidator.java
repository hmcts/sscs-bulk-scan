package uk.gov.hmcts.reform.sscs.validators;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.KeyValuePairValidator;

@Component
public class SscsKeyValuePairValidator implements KeyValuePairValidator {

    private static final String VALUE = "value";
    private static final String KEY = "key";

    private final String schemaResourceLocation;
    private Schema schema = null;

    @Autowired
    public SscsKeyValuePairValidator(@Value("${schema.location}") String schemaResourceLocation) {
        this.schemaResourceLocation = schemaResourceLocation;
    }

    @Override
    public CaseResponse validate(Map<String, Object> keyValuePairs) {
        tryLoadSchema();

        try {
            schema.validate(new JSONObject(buildOcrData(keyValuePairs)));
        } catch (ValidationException ex) {
            List<String> errors = new ArrayList<>();

            for (String message : ex.getAllMessages()) {
                errors.add(message);
            }
            return CaseResponse.builder().errors(errors).build();
        }
        return CaseResponse.builder().build();
    }

    private Map<String, Object> buildOcrData(Map<String, Object> exceptionCaseData) {
        Map<String, Object> pairs = new HashMap<>();

        JSONArray jsonArray = new JSONArray((List) exceptionCaseData.get("scanOCRData"));
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject j = jsonArray.optJSONObject(i);

            JSONObject jsonObject = (JSONObject) j.get(VALUE);

            String jsonValue = jsonObject.has(VALUE) ? jsonObject.get(VALUE).toString() : null;

            pairs.put(jsonObject.get(KEY).toString(), jsonValue);
        }
        return pairs;
    }

    private synchronized void tryLoadSchema() {

        if (schema != null) {
            return;
        }

        InputStream inputStream = getClass().getResourceAsStream(schemaResourceLocation);
        schema = SchemaLoader.load(new JSONObject(new JSONTokener(inputStream)));
    }
}
