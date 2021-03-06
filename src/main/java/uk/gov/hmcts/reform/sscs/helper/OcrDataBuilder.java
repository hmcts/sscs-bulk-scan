package uk.gov.hmcts.reform.sscs.helper;

import io.micrometer.core.instrument.util.StringUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.OcrDataField;

public class OcrDataBuilder {


    private OcrDataBuilder() {
    }


    public static Map<String, Object> build(List<OcrDataField> exceptionCaseData) {
        Map<String, Object> pairs = new HashMap<>();

        if (exceptionCaseData != null) {
            for (OcrDataField ocrDataField : exceptionCaseData) {
                if (!StringUtils.isEmpty(ocrDataField.getName())) {
                    String value = !StringUtils.isEmpty(ocrDataField.getValue()) ? ocrDataField.getValue() : null;
                    pairs.put(ocrDataField.getName(), value);
                }
            }
        }

        return pairs;
    }
}
