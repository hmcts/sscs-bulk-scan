package uk.gov.hmcts.reform.sscs.common;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SampleCaseDataCreator {

    public Map<String, Object> caseData() {
        return ImmutableMap.of(
            "journeyClassification", "New Application",
            "poBoxJurisdiction", "SSCS",
            "poBox", "SSCSPO",
            "openingDate", "2018-01-11",
            "scanRecords", ocrData()
        );
    }

    private List<ScannedOcrData> ocrData() {
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
