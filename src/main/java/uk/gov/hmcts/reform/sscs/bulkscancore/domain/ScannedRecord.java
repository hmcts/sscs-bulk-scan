package uk.gov.hmcts.reform.sscs.bulkscancore.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.sscs.ccd.domain.DocumentLink;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannedRecord {

    private DocumentLink documentLink;
    private String docScanDate;
    private String filename;
    private String documentType;

    @JsonIgnore
    private String documentControlNumber;

}
