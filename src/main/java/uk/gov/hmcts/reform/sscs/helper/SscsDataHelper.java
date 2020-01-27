package uk.gov.hmcts.reform.sscs.helper;

import static org.springframework.util.ObjectUtils.isEmpty;
import static uk.gov.hmcts.reform.sscs.ccd.domain.State.READY_TO_LIST;
import static uk.gov.hmcts.reform.sscs.ccd.domain.State.VALID_APPEAL;
import static uk.gov.hmcts.reform.sscs.service.CaseCodeService.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.model.dwp.OfficeMapping;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;

@Component
public class SscsDataHelper {

    private final CaseEvent caseEvent;

    private final List<String> offices;

    private final DwpAddressLookupService dwpAddressLookupService;

    public SscsDataHelper(CaseEvent caseEvent,
                          @Value("#{'${readyToList.offices}'.split(',')}") List<String> offices,
                          DwpAddressLookupService dwpAddressLookupService) {
        this.caseEvent = caseEvent;
        this.offices = offices;
        this.dwpAddressLookupService = dwpAddressLookupService;
    }

    public void addSscsDataToMap(Map<String, Object> appealData, Appeal appeal, List<SscsDocument> sscsDocuments, Subscriptions subscriptions) {
        appealData.put("appeal", appeal);
        appealData.put("sscsDocument", sscsDocuments);
        appealData.put("evidencePresent", hasEvidence(sscsDocuments));
        appealData.put("subscriptions", subscriptions);


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

                if (appeal.getMrnDetails() != null && appeal.getMrnDetails().getDwpIssuingOffice() != null) {
                    Optional<OfficeMapping> officeMapping = dwpAddressLookupService.getDwpMappingByOffice(
                        appeal.getBenefitType().getCode(),
                        appeal.getMrnDetails().getDwpIssuingOffice());

                    officeMapping.ifPresent(office -> appealData.put("dwpRegionalCentre", office.getMapping().getDwpRegionCentre()));
                }
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

    public String getCreatedInGapsFromField(Appeal appeal) {

        if (null != appeal && null != appeal.getMrnDetails() && null != appeal.getMrnDetails().getDwpIssuingOffice()) {
            return offices.contains(appeal.getMrnDetails().getDwpIssuingOffice()) ? READY_TO_LIST.getId() : VALID_APPEAL.getId();
        }
        return null;
    }

}
