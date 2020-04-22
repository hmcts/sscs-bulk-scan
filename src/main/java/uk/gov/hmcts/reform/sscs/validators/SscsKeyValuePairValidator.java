package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus.ERRORS;
import static uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus.SUCCESS;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilder.build;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilder.buildOld;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.OcrDataField;
import uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus;

@Component
public class SscsKeyValuePairValidator {

    private final String schemaResourceLocation = "/schema/sscs-bulk-scan-schema.json";

    private Schema schema = null;

    //FIXME: Remove after bulk scan migration
    public CaseResponse validateOld(Map<String, Object> keyValuePairs, String property) {
        tryLoadSchema(schemaResourceLocation);

        List<String> errors = null;

        try {
            Map<String, Object> builtMap = property.equals("ocr_data_fields") ? null : buildOld(keyValuePairs, property);
            schema.validate(new JSONObject(builtMap));
        } catch (ValidationException ex) {
            errors = new ArrayList<>();
            for (String message : ex.getAllMessages()) {
                errors.add(message);
            }
        }
        return CaseResponse.builder().errors(errors).status(getValidationStatus(errors)).build();
    }

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
        return CaseResponse.builder().errors(errors).status(getValidationStatus(errors)).build();
    }

    private synchronized void tryLoadSchema(String schemaLocation) {

        if (schema != null) {
            return;
        }

        InputStream inputStream = getClass().getResourceAsStream(schemaLocation);
        schema = SchemaLoader.load(new JSONObject(new JSONTokener(inputStream)));
    }

    private ValidationStatus getValidationStatus(List<String> errors) {
        if (!ObjectUtils.isEmpty(errors)) {
            return ERRORS;
        }
        return SUCCESS;
    }
}
