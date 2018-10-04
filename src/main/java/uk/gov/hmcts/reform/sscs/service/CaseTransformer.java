package uk.gov.hmcts.reform.sscs.service;

import uk.gov.hmcts.reform.sscs.domain.CreateCaseEvent;

import java.util.Map;

public interface CaseTransformer {
    Map<String, Object> transformExceptionRecordToCase(CreateCaseEvent createCaseEvent);
}
