package uk.gov.hmcts.reform.sscs.helper;

import static org.springframework.util.ObjectUtils.isEmpty;
import static uk.gov.hmcts.reform.sscs.ccd.domain.State.READY_TO_LIST;
import static uk.gov.hmcts.reform.sscs.ccd.domain.State.VALID_APPEAL;
import static uk.gov.hmcts.reform.sscs.service.CaseCodeService.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.MrnDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocument;
import uk.gov.hmcts.reform.sscs.ccd.domain.Subscriptions;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;

@Component
public class SscsDataHelper {

    private final CaseEvent caseEvent;

    private final List<String> offices;


    public SscsDataHelper(CaseEvent caseEvent,
                          @Value("#{'${readyToList.offices}'.split(',')}") List<String> offices) {
        this.caseEvent = caseEvent;
        this.offices = offices;
    }

    public void addSscsDataToMap(Map<String, Object> appealData, Appeal appeal, List<SscsDocument> sscsDocuments, Subscriptions subscriptions) {
        appealData.put("appeal", appeal);
        appealData.put("sscsDocument", sscsDocuments);
        appealData.put("evidencePresent", hasEvidence(sscsDocuments));
        appealData.put("subscriptions", subscriptions);
        appealData.put("interlocReviewState", null);

        if (appeal != null) {
            if (appeal.getAppellant() != null) {
                if (appeal.getAppellant().getName() != null) {
                    appealData.put("generatedSurname", appeal.getAppellant().getName().getLastName());
                }
                if (appeal.getAppellant().getIdentity() != null) {
                    appealData.put("generatedNino", appeal.getAppellant().getIdentity().getNino());
                    appealData.put("generatedDOB", appeal.getAppellant().getIdentity().getDob());
                }
            }
            if (appeal.getBenefitType() != null) {
                String benefitCode = generateBenefitCode(appeal.getBenefitType().getCode());
                String issueCode = generateIssueCode();

                appealData.put("benefitCode", benefitCode);
                appealData.put("issueCode", issueCode);
                appealData.put("caseCode", generateCaseCode(benefitCode, issueCode));
            }
            appealData.put("createdInGapsFrom", getCreatedInGapsFromField(appeal));
        }
    }

    public String findEventToCreateCase(CaseResponse caseValidationResponse) {
        LocalDate mrnDate = findMrnDateTime(((Appeal) caseValidationResponse.getTransformedCase()
            .get("appeal")).getMrnDetails());

        if (!isEmpty(caseValidationResponse.getWarnings())) {
            return caseEvent.getIncompleteApplicationEventId();
        } else if (mrnDate != null && mrnDate.plusMonths(13L).isBefore(LocalDate.now())) {
            return caseEvent.getNonCompliantEventId();
        } else {
            return caseEvent.getValidAppealCreatedEventId();
        }
    }

    private LocalDate findMrnDateTime(MrnDetails mrnDetails) {
        if (mrnDetails != null && mrnDetails.getMrnDate() != null) {
            return LocalDate.parse(mrnDetails.getMrnDate());
        }
        return null;
    }

    public String hasEvidence(List<SscsDocument> sscsDocuments) {
        return (null == sscsDocuments || sscsDocuments.isEmpty()) ? "No" : "Yes";
    }

    private String getCreatedInGapsFromField(Appeal appeal) {

        if (appeal.getMrnDetails() != null && appeal.getMrnDetails().getDwpIssuingOffice() != null) {
            return offices.contains(appeal.getMrnDetails().getDwpIssuingOffice()) ? READY_TO_LIST.getId() : VALID_APPEAL.getId();

        }
        return null;
    }
}
