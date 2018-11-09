package uk.gov.hmcts.reform.sscs.bulkscancore.domain;

import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CaseResponse {
    @ApiModelProperty(value = "Warning messages")
    private List<String> warnings;
    @ApiModelProperty(value = "Transformed case")
    private Map<String, Object> transformedCase;
    @ApiModelProperty(value = "Error messages")
    private List<String> errors;

}
