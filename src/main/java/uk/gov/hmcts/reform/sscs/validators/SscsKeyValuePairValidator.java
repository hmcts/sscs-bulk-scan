package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilder.build;
import static uk.gov.hmcts.reform.sscs.helper.SscsDataHelper.getValidationStatus;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class SscsKeyValuePairValidator {

    private Schema sscs1Schema = tryLoadSscsSchema("/schema/sscs-bulk-scan-schema.json");
    private Schema sscs2Schema = tryLoadSscsSchema("/schema/sscs2-bulk-scan-schema.json");
    private Schema sscs5Schema = tryLoadSscsSchema("/schema/sscs5-bulk-scan-schema.json");

    public CaseResponse validate(List<OcrDataField> ocrDataValidationRequest, FormType formType) {
        List<String> errors = null;

        try {
            log.info("Validating against formType {}", formType);

            if (formType != null && formType.equals(FormType.SSCS2)) {
                sscs2Schema.validate(new JSONObject(build(ocrDataValidationRequest)));
            } else if (formType != null && formType.equals(FormType.SSCS5)) {
                sscs5Schema.validate(new JSONObject(build(ocrDataValidationRequest)));
            } else if (formType != null && (formType.equals(FormType.SSCS1U) || formType.equals(FormType.SSCS1)
                || formType.equals(FormType.SSCS1PE) || formType.equals(FormType.SSCS1PEU))) {
                sscs1Schema.validate(new JSONObject(build(ocrDataValidationRequest)));
            }
        } catch (ValidationException ex) {
            log.error("Validation failed: {}", ex.getAllMessages());
            errors = new ArrayList<>();
            for (String message : ex.getAllMessages()) {
                errors.add(message);
            }
        }
        return CaseResponse.builder().errors(errors).warnings(new ArrayList<>())
            .status(getValidationStatus(errors, null)).build();
    }

    private synchronized Schema tryLoadSscsSchema(String schemaLocation) {
        return SchemaLoader
            .load(new JSONObject(new JSONTokener(getClass().getResourceAsStream(schemaLocation))));
    }

}
