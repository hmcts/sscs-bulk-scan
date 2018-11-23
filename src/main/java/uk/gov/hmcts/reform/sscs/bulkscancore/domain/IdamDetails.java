package uk.gov.hmcts.reform.sscs.bulkscancore.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IdamDetails {

    private String idamOauth2Token;
    private String userId;
    private String idamToken;
}
