package uk.gov.hmcts.reform.sscs.handler;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocument;
import uk.gov.hmcts.reform.sscs.ccd.service.SscsCcdConvertService;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.service.EvidenceManagementService;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;
import uk.gov.hmcts.reform.sscs.service.RoboticsService;

@Component
@Slf4j
public class SscsRoboticsHandler {


    private final RoboticsService roboticsService;
    private final RegionalProcessingCenterService regionalProcessingCenterService;
    private final SscsCcdConvertService convertService;
    private final EvidenceManagementService evidenceManagementService;
    private final CaseEvent caseEvent;
    private final Boolean roboticsEnabled;

    public SscsRoboticsHandler(RoboticsService roboticsService,
                               RegionalProcessingCenterService regionalProcessingCenterService,
                               SscsCcdConvertService convertService,
                               EvidenceManagementService evidenceManagementService,
                               CaseEvent caseEvent,
                               @Value("${robotics.email.enabled}") Boolean roboticsEnabled) {
        this.roboticsService = roboticsService;
        this.regionalProcessingCenterService = regionalProcessingCenterService;
        this.convertService = convertService;
        this.evidenceManagementService = evidenceManagementService;
        this.caseEvent = caseEvent;
        this.roboticsEnabled = roboticsEnabled;
    }

    public void handle(CaseResponse caseValidationResponse, Long caseId, String eventId) {
        if (roboticsEnabled && eventId.equals(caseEvent.getCaseCreatedEventId())) {
            SscsCaseData sscsCaseData = convertService.getCaseData(caseValidationResponse.getTransformedCase());

            Map<String, byte[]> additionalEvidence = downloadEvidence(sscsCaseData);

            roboticsService.sendCaseToRobotics(sscsCaseData,
                caseId,
                regionalProcessingCenterService.getFirstHalfOfPostcode(sscsCaseData.getAppeal().getAppellant().getAddress().getPostcode()),
                null,
                additionalEvidence);

        }
    }

    private Map<String, byte[]> downloadEvidence(SscsCaseData sscsCaseData) {
        if (hasEvidence(sscsCaseData)) {
            Map<String, byte[]> map = new LinkedHashMap<>();
            for (SscsDocument doc : sscsCaseData.getSscsDocument()) {
                map.put(doc.getValue().getDocumentFileName(), downloadBinary(doc));
            }
            return map;
        } else {
            return Collections.emptyMap();
        }
    }

    private boolean hasEvidence(SscsCaseData sscsCaseData) {
        return CollectionUtils.isNotEmpty(sscsCaseData.getSscsDocument());
    }

    private byte[] downloadBinary(SscsDocument doc) {
        return evidenceManagementService.download(URI.create(doc.getValue().getDocumentLink().getDocumentUrl()), null);
    }
}
