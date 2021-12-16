package uk.gov.hmcts.reform.sscs.helper;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.util.ObjectUtils.isEmpty;
import static uk.gov.hmcts.reform.sscs.ccd.domain.State.READY_TO_LIST;
import static uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus.*;
import static uk.gov.hmcts.reform.sscs.service.CaseCodeService.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus;
import uk.gov.hmcts.reform.sscs.model.dwp.OfficeMapping;
import uk.gov.hmcts.reform.sscs.service.AirLookupService;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.validators.PostcodeValidator;

@Component
@Slf4j
public class SscsDataHelper {

    private final CaseEvent caseEvent;

    private final DwpAddressLookupService dwpAddressLookupService;

    private final AirLookupService airLookupService;

    private final PostcodeValidator postcodeValidator;


    public SscsDataHelper(CaseEvent caseEvent,
                          DwpAddressLookupService dwpAddressLookupService,
                          AirLookupService airLookupService,
                          PostcodeValidator postcodeValidator) {
        this.caseEvent = caseEvent;
        this.dwpAddressLookupService = dwpAddressLookupService;
        this.airLookupService = airLookupService;
        this.postcodeValidator = postcodeValidator;
    }

    public void addSscsDataToMap(Map<String, Object> appealData, Appeal appeal, List<SscsDocument> sscsDocuments, Subscriptions subscriptions,
                                 FormType formType, String childMaintenanceNumber,
                                 List<CcdValue<OtherParty>> otherParties) {
        appealData.put("appeal", appeal);
        appealData.put("sscsDocument", sscsDocuments);
        appealData.put("evidencePresent", hasEvidence(sscsDocuments));
        appealData.put("subscriptions", subscriptions);
        appealData.put("formType", formType);

        if (appeal != null) {
            if (appeal.getBenefitType() != null && isNotBlank(appeal.getBenefitType().getCode())) {
                String benefitCode = null;
                String addressName = null;
                if (appeal.getMrnDetails() != null) {
                    addressName = appeal.getMrnDetails().getDwpIssuingOffice();
                }
                benefitCode = generateBenefitCode(appeal.getBenefitType().getCode(), addressName).orElse(EMPTY);

                String issueCode = generateIssueCode();

                appealData.put("benefitCode", benefitCode);
                appealData.put("issueCode", issueCode);
                appealData.put("caseCode", generateCaseCode(benefitCode, issueCode));

                String dwpRegionCentre = setDwpRegionalCenter(appealData, appeal);
                if (dwpRegionCentre != null) {
                    appealData.put("dwpRegionalCentre", dwpRegionCentre);
                }
            }
            appealData.put("createdInGapsFrom", READY_TO_LIST.getId());
            checkConfidentiality(formType,appealData, appeal);
        }

        if (FormType.SSCS2.equals(formType)) {
            appealData.put("childMaintenanceNumber", childMaintenanceNumber);
            if (otherParties != null) {
                appealData.put("otherParties", otherParties);
            }
        }
    }

    private void checkConfidentiality(FormType formType, Map<String, Object> appealData, Appeal appeal) {
        if ((FormType.SSCS2.equals(formType) || FormType.SSCS5.equals(formType)) && appeal.getAppellant() != null && YesNo.isYes(appeal.getAppellant().getConfidentialityRequired())) {
            appealData.put("isConfidentialCase", YesNo.YES.getValue());
        }
    }

    private String setDwpRegionalCenter(Map<String, Object> appealData, Appeal appeal) {
        String dwpRegionCentre = null;
        if (appeal.getMrnDetails() != null && appeal.getMrnDetails().getDwpIssuingOffice() != null) {
            dwpRegionCentre = dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice(
                appeal.getBenefitType().getCode(),
                appeal.getMrnDetails().getDwpIssuingOffice());
            log.info("DwpHandling office set as " + dwpRegionCentre);
        } else if (appeal.getMrnDetails() == null || appeal.getMrnDetails().getDwpIssuingOffice() == null) {
            Optional<OfficeMapping> defaultOfficeMapping = dwpAddressLookupService.getDefaultDwpMappingByBenefitType(appeal.getBenefitType().getCode());
            if (defaultOfficeMapping.isPresent()) {
                String defaultDwpIssuingOffice = defaultOfficeMapping.get().getMapping().getCcd();
                dwpRegionCentre = dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice(
                    appeal.getBenefitType().getCode(),
                    defaultDwpIssuingOffice);
                log.info("Default dwpHandling office set as " + dwpRegionCentre);
            }
        }
        return dwpRegionCentre;
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

    public static ValidationStatus getValidationStatus(List<String> errors, List<String> warnings) {
        if (!ObjectUtils.isEmpty(errors)) {
            return ERRORS;
        }
        if (!ObjectUtils.isEmpty(warnings)) {
            return WARNINGS;
        }
        return SUCCESS;
    }

    public String findProcessingVenue(Appellant appellant, BenefitType benefitType) {
        if (appellant != null && benefitType != null && isNotBlank(benefitType.getCode())) {
            Appointee appointee = appellant.getAppointee();
            String postcode = "";
            if (appointee != null && appointee.getAddress() != null && isValidPostcode(appointee.getAddress().getPostcode())) {
                postcode = appointee.getAddress().getPostcode();
            } else if (appellant.getAddress() != null && isValidPostcode(appellant.getAddress().getPostcode())) {
                postcode = appellant.getAddress().getPostcode();
            }

            if (isNotBlank(postcode)) {
                return airLookupService.lookupAirVenueNameByPostCode(postcode, benefitType);
            }
        }
        return null;
    }

    private boolean isValidPostcode(String postcode) {
        return postcodeValidator.isValidPostcodeFormat(postcode) && postcodeValidator.isValid(postcode);
    }

}
