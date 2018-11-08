package uk.gov.hmcts.reform.sscs.bulkscancore.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder

public class IdamToken {

    private String userAuthToken;
    private String serviceAuthToken;
    private String userId;

}
