package uk.gov.hmcts.reform.sscs.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@ToString
public class CreateCaseEvent {
    private String token;
    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("case_details")
    private CaseDetails caseDetails;
}
