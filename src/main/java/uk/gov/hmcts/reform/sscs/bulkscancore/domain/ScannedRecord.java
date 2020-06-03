package uk.gov.hmcts.reform.sscs.bulkscancore.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.sscs.ccd.domain.DocumentLink;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScannedRecord {

    //FIXME: Can probably delete this after migration
    private DocumentLink url;
    private String scannedDate;
    private String fileName;
    private String type;
    private String subtype;

    @JsonIgnore
    private String controlNumber;

}
