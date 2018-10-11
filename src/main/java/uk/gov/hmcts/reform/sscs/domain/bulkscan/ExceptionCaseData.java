package uk.gov.hmcts.reform.sscs.domain.bulkscan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class ExceptionCaseData {
    private String token;
    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("case_details")
    private CaseDetails caseDetails;
}
