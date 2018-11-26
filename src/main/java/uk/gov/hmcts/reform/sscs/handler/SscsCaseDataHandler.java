package uk.gov.hmcts.reform.sscs.handler;

import static org.springframework.util.ObjectUtils.isEmpty;

import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.ccd.CaseDataHelper;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.HandlerResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CaseDataHandler;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.MrnDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocument;
import uk.gov.hmcts.reform.sscs.ccd.service.SscsCcdConvertService;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.service.EvidenceManagementService;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;
import uk.gov.hmcts.reform.sscs.service.RoboticsService;

@Component
@Slf4j
public class SscsCaseDataHandler implements CaseDataHandler {

    private final CaseDataHelper caseDataHelper;
    private final RoboticsService roboticsService;
    private final RegionalProcessingCenterService regionalProcessingCenterService;
    private final SscsCcdConvertService convertService;
    private final EvidenceManagementService evidenceManagementService;
    private final CaseEvent caseEvent;

    public SscsCaseDataHandler(CaseDataHelper caseDataHelper,
                               RoboticsService roboticsService,
                               RegionalProcessingCenterService regionalProcessingCenterService,
                               SscsCcdConvertService convertService,
                               EvidenceManagementService evidenceManagementService,
                               CaseEvent caseEvent) {
        this.caseDataHelper = caseDataHelper;
        this.roboticsService = roboticsService;
        this.regionalProcessingCenterService = regionalProcessingCenterService;
        this.convertService = convertService;
        this.evidenceManagementService = evidenceManagementService;
        this.caseEvent = caseEvent;
    }

    public CallbackResponse handle(CaseResponse caseValidationResponse,
                                   boolean ignoreWarnings,
                                   Token token,
                                   String exceptionRecordId) {

        if (canCreateCase(caseValidationResponse, ignoreWarnings)) {
            String eventId = findEventToCreateCase(caseValidationResponse);

            Long caseId = caseDataHelper.createCase(caseValidationResponse.getTransformedCase(), token.getUserAuthToken(), token.getServiceAuthToken(), token.getUserId(), eventId);

            if (eventId.equals(caseEvent.getCaseCreatedEventId())) {
                SscsCaseData sscsCaseData = convertService.getCaseData(caseValidationResponse.getTransformedCase());

                Map<String, byte[]> additionalEvidence = downloadEvidence(sscsCaseData);

                roboticsService.sendCaseToRobotics(sscsCaseData,
                    caseId,
                    regionalProcessingCenterService.getFirstHalfOfPostcode(sscsCaseData.getAppeal().getAppellant().getAddress().getPostcode()),
                    null,
                    additionalEvidence);
            }

            log.info("Case created with exceptionRecordId {} from exception record id {}", caseId, exceptionRecordId);

            return HandlerResponse.builder().state("ScannedRecordCaseCreated").caseId(String.valueOf(caseId)).build();
        }
        return null;
    }

    private Boolean canCreateCase(CaseResponse caseValidationResponse, boolean ignoreWarnings) {
        return ((!isEmpty(caseValidationResponse.getWarnings()) && ignoreWarnings) || isEmpty(caseValidationResponse.getWarnings()));
    }

    private String findEventToCreateCase(CaseResponse caseValidationResponse) {
        LocalDate mrnDate = findMrnDateTime(((Appeal) caseValidationResponse.getTransformedCase().get("appeal")).getMrnDetails());

        if (!isEmpty(caseValidationResponse.getWarnings())) {
            return caseEvent.getIncompleteApplicationEventId();
        } else if (mrnDate != null && mrnDate.plusMonths(13L).isBefore(LocalDate.now())) {
            return caseEvent.getNonCompliantEventId();
        } else {
            return caseEvent.getCaseCreatedEventId();
        }
    }

    private LocalDate findMrnDateTime(MrnDetails mrnDetails) {
        if (mrnDetails != null && mrnDetails.getMrnDate() != null) {
            return LocalDate.parse(mrnDetails.getMrnDate());
        }
        return null;
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
        return evidenceManagementService.download(URI.create(doc.getValue().getDocumentLink().getDocumentUrl()));
    }
}
