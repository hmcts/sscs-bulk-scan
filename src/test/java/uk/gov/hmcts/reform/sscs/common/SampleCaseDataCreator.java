package uk.gov.hmcts.reform.sscs.common;

import com.google.common.collect.ImmutableMap;
import java.util.*;

public class SampleCaseDataCreator {

    public Map<String, Object> exceptionCaseData() {
        // Can't use Immutable map here as it will be modified by the handler
        Map<String, Object> exceptionRecord = new HashMap<>();
        exceptionRecord.put("journeyClassification", "New Application");
        exceptionRecord.put("poBoxJurisdiction", "SSCS");
        exceptionRecord.put("poBox", "SSCSPO");
        exceptionRecord.put("openingDate", "2018-01-11");
        exceptionRecord.put("scanRecords", ocrData());

        return exceptionRecord;
    }

    public Map<String, Object> sscsCaseData() {
        return ImmutableMap.of(
            "generatedEmail", "sscstest@test.com",
            "caseReference", "123456789",
            "caseCreated", "2018-01-11",
            "generatedNino", "SR11111",
            "generatedSurname", "Smith"
        );
    }

    public List<ScannedOcrData> ocrData() {
        ScannedOcrData ocrData1 = new ScannedOcrData();
        ocrData1.setValue(ImmutableMap.of("key", "firstName", "value", "John"));
        ocrData1.setId("d55a7f14-92c3-4134-af78-f2aa2b201841");

        ScannedOcrData ocrData2 = new ScannedOcrData();
        ocrData2.setValue(ImmutableMap.of("key", "lastName", "value", "Smith"));
        ocrData2.setId("d55a7f14-92c3-4134-af78-f2aa2b201841");

        List<ScannedOcrData> ocrDataArray = new ArrayList<>();
        ocrDataArray.add(ocrData1);
        ocrDataArray.add(ocrData2);

        return ocrDataArray;
    }

    // This class is required as jackson cannot deserialize JsonObject if we use it to create scanned ocr data
    public class ScannedOcrData {
        private Map<String, Object> value;
        private String id;

        // Equals and hashcode are required for AssertJ to compare
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ScannedOcrData that = (ScannedOcrData) o;
            return Objects.equals(value, that.value)
                && Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {

            return Objects.hash(value, id);
        }

        public Map<String, Object> getValue() {
            return value;
        }

        private void setValue(Map<String, Object> value) {
            this.value = value;
        }

        public String getId() {
            return id;
        }

        private void setId(String id) {
            this.id = id;
        }
    }
}
