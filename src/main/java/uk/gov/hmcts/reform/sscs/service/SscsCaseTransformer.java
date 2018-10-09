package uk.gov.hmcts.reform.sscs.service;

import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.domain.CreateCaseEvent;

import java.util.Map;
import java.util.UUID;

@Component
public class SscsCaseTransformer implements CaseTransformer {
    @Override
    public Map<String, Object> transformExceptionRecordToCase(Map<String, Object> exceptionCaseData) {
        //transform into actual case
        return ImmutableMap.of(
            "caseReference", UUID.randomUUID().toString(),
            "generatedNino", "12344SR",
            "generatedSurname", "Test",
            "generatedEmail", "test@test.com"
        );

    }
}
