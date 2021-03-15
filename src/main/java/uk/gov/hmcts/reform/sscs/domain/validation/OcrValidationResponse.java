package uk.gov.hmcts.reform.sscs.domain.validation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OcrValidationResponse {

    @JsonProperty("warnings")
    public final List<String> warnings;

    @JsonProperty("errors")
    public final List<String> errors;

    @JsonProperty("status")
    public final ValidationStatus status;

    @JsonCreator
    public OcrValidationResponse(
        List<String> warnings,
        List<String> errors,
        ValidationStatus status
    ) {
        this.warnings = warnings;
        this.errors = errors;
        this.status = status;
    }
}
