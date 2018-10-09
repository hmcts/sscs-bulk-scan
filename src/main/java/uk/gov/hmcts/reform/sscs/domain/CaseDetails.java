package uk.gov.hmcts.reform.sscs.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@ToString
public class CaseDetails {

    @JsonProperty("case_data")
    private Map<String, Object> caseData;

    @JsonProperty("id")
    private String caseId;

    private String state;
}
