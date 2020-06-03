package uk.gov.hmcts.reform.sscs.domain.transformation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class SuccessfulTransformationResponse {

    @JsonProperty("case_creation_details")
    private final CaseCreationDetails caseCreationDetails;

    @JsonProperty("warnings")
    private final List<String> warnings;

    // region constructor
    public SuccessfulTransformationResponse(
        CaseCreationDetails caseCreationDetails,
        List<String> warnings
    ) {
        this.caseCreationDetails = caseCreationDetails;
        this.warnings = warnings;
    }
    // endregion
}
