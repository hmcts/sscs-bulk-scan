package uk.gov.hmcts.reform.sscs.bulkscancore.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExceptionRecord {

    private final String id;
    private final String caseTypeId;
    private final String poBox;
    private final String jurisdiction;
    private final String formType;
    private final JourneyClassification journeyClassification;
    private final LocalDateTime deliveryDate;
    private final LocalDateTime openingDate;
    private final List<InputScannedDoc> scannedDocuments;
    private final List<OcrDataField> ocrDataFields;

    public ExceptionRecord(
        @JsonProperty("id") String id,
        @JsonProperty("case_type_id") String caseTypeId,
        @JsonProperty("po_box") String poBox,
        @JsonProperty("po_box_jurisdiction") String jurisdiction,
        @JsonProperty("form_type") String formType,
        @JsonProperty("journey_classification") JourneyClassification journeyClassification,
        @JsonProperty("delivery_date") LocalDateTime deliveryDate,
        @JsonProperty("opening_date") LocalDateTime openingDate,
        @JsonProperty("scanned_documents") List<InputScannedDoc> scannedDocuments,
        @JsonProperty("ocr_data_fields") List<OcrDataField> ocrDataFields
    ) {
        this.id = id;
        this.caseTypeId = caseTypeId;
        this.poBox = poBox;
        this.jurisdiction = jurisdiction;
        this.formType = formType;
        this.journeyClassification = journeyClassification;
        this.deliveryDate = deliveryDate;
        this.openingDate = openingDate;
        this.scannedDocuments = scannedDocuments;
        this.ocrDataFields = ocrDataFields;
    }
}
