package uk.gov.hmcts.reform.sscs.transformers;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseTransformationResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;
import uk.gov.hmcts.reform.sscs.ccd.domain.Name;

@Component
public class SscsCaseTransformer implements CaseTransformer {

    @Override
    public CaseTransformationResponse transformExceptionRecordToCase(Map<String, Object> exceptionCaseData) {

        Map<String, Object> transformed = new HashMap<>();

        Appeal appeal = Appeal.builder().appellant(Appellant.builder().name(Name.builder().title("Mr").firstName("Jack").lastName("Maloney").build()).build()).build();

        transformed.put("appeal", appeal);

        return CaseTransformationResponse.builder().transformedCase(transformed).build();

    }
}
