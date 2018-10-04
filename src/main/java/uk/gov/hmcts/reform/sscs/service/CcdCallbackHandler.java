package uk.gov.hmcts.reform.sscs.service;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.domain.CaseValidationResponse;
import uk.gov.hmcts.reform.sscs.domain.CcdCallbackResponse;
import uk.gov.hmcts.reform.sscs.domain.CreateCaseEvent;

import java.util.Map;
import java.util.Objects;

@Component
public class CcdCallbackHandler {

    private final CaseTransformer caseTransformer;

    private final CaseValidator caseValidator;

    public CcdCallbackHandler(CaseTransformer caseTransformer,
                              CaseValidator caseValidator
    ) {
        this.caseTransformer = caseTransformer;
        this.caseValidator = caseValidator;
    }


    public CcdCallbackResponse handle(CreateCaseEvent createCaseEvent, String authorisationToken) {


        // check auth token ?

        // Transform into sscs case
        Map<String, Object> exceptionRecordData = createCaseEvent.getCaseDetails().getCaseData();

        Map<String, Object> sscsCaseCaseData = caseTransformer.transformExceptionRecordToCase(createCaseEvent);

        // Validate with robotics schema
        CaseValidationResponse caseValidationResponse = caseValidator.validate(sscsCaseCaseData);

        if (Objects.nonNull(caseValidationResponse.getErrors()) && Objects.nonNull(caseValidationResponse.getWarnings())) {
            // Start event -create case
            // Submit event - create case
            return CcdCallbackResponse.builder()
                .data(exceptionRecordData)
                .build();
        }

        return CcdCallbackResponse.builder()
            .data(exceptionRecordData)
            .errors(caseValidationResponse.getErrors())
            .warnings(caseValidationResponse.getWarnings())
            .build();
    }

}
