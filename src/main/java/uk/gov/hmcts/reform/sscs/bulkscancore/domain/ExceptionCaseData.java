package uk.gov.hmcts.reform.sscs.bulkscancore.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExceptionCaseData {
    private String token;
    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("case_details")
    private CaseDetails caseDetails;
    @JsonProperty("ignore_warning")
    private boolean ignoreWarnings;
}
