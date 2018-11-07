package uk.gov.hmcts.reform.sscs.handler;

import static org.springframework.util.ObjectUtils.isEmpty;

import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;
import uk.gov.hmcts.reform.sscs.service.RoboticsService;
import uk.gov.hmcts.reform.sscs.service.SscsPdfService;

@Component
@Slf4j
public class SscsCaseDataHandler implements CaseDataHandler {

    private final CaseDataHelper caseDataHelper;
    private final SscsPdfService sscsPdfService;
    private final RoboticsService roboticsService;
    private final RegionalProcessingCenterService regionalProcessingCenterService;
    private final CcdService ccdService;
    private final String caseCreatedEventId;
    private final String incompleteApplicationEventId;

    private final String nonCompliantEventId;

    public SscsCaseDataHandler(CaseDataHelper caseDataHelper,
                               SscsPdfService sscsPdfService,
                               RoboticsService roboticsService,
                               RegionalProcessingCenterService regionalProcessingCenterService,
                               CcdService ccdService,
                               @Value("${ccd.case.caseCreatedEventId}") String caseCreatedEventId,
                               @Value("${ccd.case.incompleteApplicationEventId}") String incompleteApplicationEventId,
                               @Value("${ccd.case.nonCompliantEventId}") String nonCompliantEventId) {
        this.caseDataHelper = caseDataHelper;
        this.sscsPdfService = sscsPdfService;
        this.roboticsService = roboticsService;
        this.regionalProcessingCenterService = regionalProcessingCenterService;
        this.ccdService = ccdService;
        this.caseCreatedEventId = caseCreatedEventId;
        this.incompleteApplicationEventId = incompleteApplicationEventId;
        this.nonCompliantEventId = nonCompliantEventId;
    }

    public CallbackResponse handle(CaseResponse caseValidationResponse,
                                   boolean ignoreWarnings,
                                   Token token,
                                   String exceptionRecordId) {

        if (canCreateCase(caseValidationResponse, ignoreWarnings)) {
            String eventId = findEventToCreateCase(caseValidationResponse);

            Long caseId = caseDataHelper.createCase(caseValidationResponse.getTransformedCase(), token.getUserAuthToken(), token.getServiceAuthToken(), token.getUserId(), eventId);

            if (eventId.equals(caseCreatedEventId)) {
                IdamTokens idamTokens = IdamTokens.builder().idamOauth2Token( token.getUserAuthToken()).serviceAuthorization(token.getServiceAuthToken()).userId(token.getUserId()).build();
                SscsCaseData sscsCaseData = ccdService.getByCaseId(caseId, idamTokens).getData();

                byte[] pdf = sscsPdfService.generateAndSendPdf(sscsCaseData, caseId, idamTokens);

                roboticsService.sendCaseToRobotics(sscsCaseData, caseId,
                    regionalProcessingCenterService.getFirstHalfOfPostcode(sscsCaseData.getAppeal().getAppellant().getAddress().getPostcode()),
                    pdf);
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
            return incompleteApplicationEventId;
        } else if (mrnDate != null && mrnDate.plusMonths(13L).isBefore(LocalDate.now())) {
            return nonCompliantEventId;
        } else {
            return caseCreatedEventId;
        }
    }

    private LocalDate findMrnDateTime(MrnDetails mrnDetails) {
        if (mrnDetails != null && mrnDetails.getMrnDate() != null) {
            return LocalDate.parse(mrnDetails.getMrnDate());
        }
        return null;
    }
}
