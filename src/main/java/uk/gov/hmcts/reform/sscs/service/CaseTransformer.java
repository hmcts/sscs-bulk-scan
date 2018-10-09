package uk.gov.hmcts.reform.sscs.service;

import java.util.Map;

public interface CaseTransformer {
    Map<String, Object> transformExceptionRecordToCase(Map<String, Object> exceptionCaseData);
}
