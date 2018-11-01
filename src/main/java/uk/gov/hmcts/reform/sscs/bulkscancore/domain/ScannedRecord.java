package uk.gov.hmcts.reform.sscs.bulkscancore.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannedRecord {

    private String documentLink;
    private String docScanDate;
    private String filename;
    private String documentType;
    private String documentControlNumber;

}
