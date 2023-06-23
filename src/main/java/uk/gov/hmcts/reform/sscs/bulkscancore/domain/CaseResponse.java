package uk.gov.hmcts.reform.sscs.bulkscancore.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus;

@Data
@Builder
public class CaseResponse {
    @Schema(title = "Warning messages")
    private List<String> warnings;
    @Schema(title = "Transformed case")
    private Map<String, Object> transformedCase;
    @Schema(title = "Error messages")
    private List<String> errors;
    @Schema(title = "Validation status")
    private ValidationStatus status;

}
