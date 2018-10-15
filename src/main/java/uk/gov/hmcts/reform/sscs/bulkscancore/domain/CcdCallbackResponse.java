package uk.gov.hmcts.reform.sscs.bulkscancore.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel(description = "The response to a callback from ccd")
@Builder
public class CcdCallbackResponse {
    @ApiModelProperty(value = "The entire case data to be returned with updated fields")
    private Map<String, Object> data;
    @ApiModelProperty(value = "Error messages")
    private List<String> errors;
    @ApiModelProperty(value = "Warning messages")
    private List<String> warnings;
}
