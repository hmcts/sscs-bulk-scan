package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus.ERRORS;
import static uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus.SUCCESS;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilder.build;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilder.build2;

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
import uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus;

@Component
public class SscsKeyValuePairValidator {

    //FIXME: Remove this schema when bulk scan migration complete
    private final String schemaResourceLocation = "/schema/sscs-bulk-scan-schema.json";
    //FIXME: Can only really do this if form type sent as part of transformation as well
    //    private final String schemaSscs1ResourceLocation = "/schema/sscs1-form-bulk-scan-schema.json";
    //    private final String schemaSscs1PeResourceLocation = "/schema/sscs1pe-form-bulk-scan-schema.json";
    private Schema schema = null;

    public CaseResponse validate(Map<String, Object> keyValuePairs, String property) {
        tryLoadSchema(schemaResourceLocation);

        List<String> errors = null;

        try {
            Map<String, Object> builtMap = property.equals("ocr_data_fields") ? build2(keyValuePairs, property) : build(keyValuePairs, property);
            schema.validate(new JSONObject(builtMap));
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
