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
import uk.gov.hmcts.reform.sscs.exception.UnknownFileTypeException;
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

    public void handle(CaseResponse caseResponse, Long caseId, String eventId) {
        if (roboticsEnabled && eventId.equals(caseEvent.getCaseCreatedEventId())) {
            log.info("Sending case to robotics for caseId {}", caseId);
            SscsCaseData sscsCaseData = convertService.getCaseData(caseResponse.getTransformedCase());

            Map<String, byte[]> additionalEvidence = downloadEvidence(sscsCaseData, caseId);

            if (additionalEvidence.size() > 0) {
                log.info("Additional evidence downloaded ready to attach to robotics for caseId {}", caseId);
            } else {
                log.info("No additional evidence downloaded for caseId {}", caseId);
            }

            try {
                roboticsService.sendCaseToRobotics(sscsCaseData,
                    caseId,
                    regionalProcessingCenterService.getFirstHalfOfPostcode(sscsCaseData.getAppeal().getAppellant().getAddress().getPostcode()),
                    null,
                    additionalEvidence);
            } catch (UnknownFileTypeException e) {
                log.error("Case {} is missing a valid filename on one of its SSCSDocuments", caseId, e);
                throw e;
            }
        }
    }

    private Map<String, byte[]> downloadEvidence(SscsCaseData sscsCaseData, Long caseId) {
        if (hasEvidence(sscsCaseData)) {
            log.info("Evidence exists so will attempt download to attach to robotics for caseId {}", caseId);
            Map<String, byte[]> map = new LinkedHashMap<>();
            for (SscsDocument doc : sscsCaseData.getSscsDocument()) {
                map.put(doc.getValue().getDocumentFileName(), downloadBinary(doc, caseId));
            }
            return map;
        } else {
            return Collections.emptyMap();
        }
    }

    private boolean hasEvidence(SscsCaseData sscsCaseData) {
        return CollectionUtils.isNotEmpty(sscsCaseData.getSscsDocument());
    }

    private byte[] downloadBinary(SscsDocument doc, Long caseId) {
        log.info("About to download binary to attach to robotics for caseId {}", caseId);
        if (doc.getValue().getDocumentLink() != null) {
            return evidenceManagementService.download(URI.create(doc.getValue().getDocumentLink().getDocumentUrl()), null);
        } else {
            return new byte[0];
        }
    }
}
