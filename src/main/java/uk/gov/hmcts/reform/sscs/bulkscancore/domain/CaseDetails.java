package uk.gov.hmcts.reform.sscs.bulkscancore.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class CaseDetails {

    @JsonProperty("case_data")
    private Map<String, Object> caseData;

    @JsonProperty("id")
    private String caseId;

    private String state;
}
