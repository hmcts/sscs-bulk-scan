package uk.gov.hmcts.reform.sscs.service;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.domain.CreateCaseEvent;

import java.util.Map;

@Component
public class SscsCaseTransformer implements CaseTransformer {
    @Override
    public Map<String, Object> transformExceptionRecordToCase(CreateCaseEvent createCaseEvent) {
        return createCaseEvent.getCaseDetails().getCaseData();
    }
}
