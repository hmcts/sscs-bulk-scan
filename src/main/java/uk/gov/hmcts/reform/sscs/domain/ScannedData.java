package uk.gov.hmcts.reform.sscs.domain;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScannedData {

    private Map<String, Object> ocrCaseData;

    private List<ScannedRecord> records;

}
