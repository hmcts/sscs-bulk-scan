package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilder.build;
import static uk.gov.hmcts.reform.sscs.helper.SscsDataHelper.getValidationStatus;

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
import uk.gov.hmcts.reform.sscs.ccd.domain.FormType;

@Component
public class SscsKeyValuePairValidator {

    private final String sscs1SchemaResourceLocation = "/schema/sscs-bulk-scan-schema.json";
    private final String sscs2SchemaResourceLocation = "/schema/sscs2-bulk-scan-schema.json";
    private final String sscs5SchemaResourceLocation = "/schema/sscs5-bulk-scan-schema.json";

    private Schema sscs1Schema = null;
    private Schema sscs2Schema = null;
    private Schema sscs5Schema = null;

    public CaseResponse validate(List<OcrDataField> ocrDataValidationRequest, FormType formType) {
        if (formType != null && formType.equals(FormType.SSCS2)) {
            tryLoadSscs2Schema();
        } else if (formType != null && formType.equals(FormType.SSCS5)) {
            tryLoadSscs5Schema();
        } else {
            tryLoadSscs1Schema();
        }

        List<String> errors = null;

        try {
            if (formType != null && formType.equals(FormType.SSCS2)) {
                sscs2Schema.validate(new JSONObject(build(ocrDataValidationRequest)));
            } else if (formType != null && formType.equals(FormType.SSCS5)) {
                sscs5Schema.validate(new JSONObject(build(ocrDataValidationRequest)));
            } else {
                sscs1Schema.validate(new JSONObject(build(ocrDataValidationRequest)));
            }
        } catch (ValidationException ex) {
            errors = new ArrayList<>();
            for (String message : ex.getAllMessages()) {
                errors.add(message);
            }
        }
        return CaseResponse.builder().errors(errors).warnings(new ArrayList<>())
            .status(getValidationStatus(errors, null)).build();
    }

    private synchronized void tryLoadSscs1Schema() {
        if (sscs1Schema != null) {
            return;
        }
        sscs1Schema = SchemaLoader
            .load(new JSONObject(new JSONTokener(getClass().getResourceAsStream(sscs1SchemaResourceLocation))));
    }

    private synchronized void tryLoadSscs2Schema() {
        if (sscs2Schema != null) {
            return;
        }
        sscs2Schema = SchemaLoader
            .load(new JSONObject(new JSONTokener(getClass().getResourceAsStream(sscs2SchemaResourceLocation))));
    }

    private synchronized void tryLoadSscs5Schema() {
        if (sscs5Schema != null) {
            return;
        }
        sscs5Schema = SchemaLoader
            .load(new JSONObject(new JSONTokener(getClass().getResourceAsStream(sscs5SchemaResourceLocation))));
    }

}
