package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilder.build;
import static uk.gov.hmcts.reform.sscs.helper.SscsDataHelper.getValidationStatus;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.OcrDataField;

@Component
public class SscsKeyValuePairValidator {

    private final String schemaResourceLocation = "/schema/sscs-bulk-scan-schema.json";

    private Schema schema = null;

    public CaseResponse validate(List<OcrDataField> ocrDataValidationRequest) {
        tryLoadSchema(schemaResourceLocation);

        List<String> errors = null;

        try {
            schema.validate(new JSONObject(build(ocrDataValidationRequest)));
        } catch (ValidationException ex) {
            errors = new ArrayList<>();
            for (String message : ex.getAllMessages()) {
                errors.add(message);
            }
        }
        return CaseResponse.builder().errors(errors).warnings(new ArrayList<>()).status(getValidationStatus(errors, null)).build();
    }

    private synchronized void tryLoadSchema(String schemaLocation) {

        if (schema != null) {
            return;
        }

        InputStream inputStream = getClass().getResourceAsStream(schemaLocation);
        schema = SchemaLoader.load(new JSONObject(new JSONTokener(inputStream)));
    }

}
