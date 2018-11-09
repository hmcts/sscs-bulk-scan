package uk.gov.hmcts.reform.sscs.bulkscancore.domain;

import lombok.Builder;
import lombok.Data;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;

@Data
@Builder
public class HandlerResponse implements CallbackResponse {

    private String state;

    private String caseId;
}
