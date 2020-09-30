package uk.gov.hmcts.reform.sscs.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.sscs.ccd.domain.State.READY_TO_LIST;
import static uk.gov.hmcts.reform.sscs.ccd.domain.State.VALID_APPEAL;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.BenefitType;
import uk.gov.hmcts.reform.sscs.ccd.domain.MrnDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocument;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.model.dwp.Mapping;
import uk.gov.hmcts.reform.sscs.model.dwp.OfficeMapping;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;

@RunWith(SpringRunner.class)
public class SscsDataHelperTest {

    private SscsDataHelper caseDataHelper;

    @Mock
    private DwpAddressLookupService dwpAddressLookupService;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private List<String> offices = new ArrayList<>();

    @Before
    public void setUp() {
        offices = new ArrayList<>();
        offices.add("3");
        offices.add("Balham DRT");

        caseDataHelper = new SscsDataHelper(new CaseEvent("appealCreated", "validAppealCreated", "incompleteApplicationReceived", "nonCompliant"), offices, dwpAddressLookupService);
    }

    @Test
    public void givenACaseResponseWithWarnings_thenReturnIncompleteCaseEvent() {
        LocalDate localDate = LocalDate.now();

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        List<String> warnings = new ArrayList<>();
        warnings.add("Warnings");
        assertEquals("incompleteApplicationReceived", caseDataHelper.findEventToCreateCase(CaseResponse.builder().transformedCase(transformedCase).warnings(warnings).build()));
    }

    @Test
    public void givenACaseResponseWithMrnDateGreaterThan13Months_thenReturnIncompleteCaseEvent() {
        LocalDate localDate = LocalDate.now().minusMonths(14);

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        assertEquals("nonCompliant", caseDataHelper.findEventToCreateCase(CaseResponse.builder().transformedCase(transformedCase).build()));
    }

    @Test
    public void givenACaseResponseWithNoWarningsAndRecentMrnDate_thenReturnCaseCreatedEvent() {
        LocalDate localDate = LocalDate.now();

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        assertEquals("validAppealCreated", caseDataHelper.findEventToCreateCase(CaseResponse.builder().transformedCase(transformedCase).build()));
    }

    @Test
    public void givenEvidenceExists_thenReturnYes() {
        List<SscsDocument> evidence = new ArrayList<>();
        evidence.add(SscsDocument.builder().build());

        assertEquals("Yes", caseDataHelper.hasEvidence(evidence));
    }

    @Test
    public void givenEvidenceDoesNotExist_thenReturnNo() {
        List<SscsDocument> evidence = new ArrayList<>();

        assertEquals("No", caseDataHelper.hasEvidence(evidence));
    }

    @Test
    public void givenAPipOfficeThatIsDigital_thenReturnReadyToList() {
        when(dwpAddressLookupService.getDwpMappingByOffice("PIP", "3")).thenReturn(Optional.of(OfficeMapping.builder().code("3").build()));
        String result = caseDataHelper.getCreatedInGapsFromField(Appeal.builder().benefitType(BenefitType.builder().code("PIP").build()).mrnDetails(MrnDetails.builder().dwpIssuingOffice("3").build()).build());

        assertEquals(READY_TO_LIST.getId(), result);
    }

    @Test
    public void givenAPipOfficeThatContainsTextAndIsDigital_thenReturnReadyToList() {
        when(dwpAddressLookupService.getDwpMappingByOffice("PIP", "My PIP Office 3")).thenReturn(Optional.of(OfficeMapping.builder().code("3").build()));
        String result = caseDataHelper.getCreatedInGapsFromField(Appeal.builder().benefitType(BenefitType.builder().code("PIP").build()).mrnDetails(MrnDetails.builder().dwpIssuingOffice("My PIP Office 3").build()).build());

        assertEquals(READY_TO_LIST.getId(), result);
    }

    @Test
    public void givenAPipOfficeThatContainsTextAndIsNonDigital_thenReturnValidAppeal() {
        when(dwpAddressLookupService.getDwpMappingByOffice("PIP", "My PIP Office 4")).thenReturn(Optional.of(OfficeMapping.builder().code("4").build()));
        String result = caseDataHelper.getCreatedInGapsFromField(Appeal.builder().benefitType(BenefitType.builder().code("PIP").build()).mrnDetails(MrnDetails.builder().dwpIssuingOffice("My PIP Office 4").build()).build());

        assertEquals(VALID_APPEAL.getId(), result);
    }

    @Test
    public void givenNoBenefitTypeThatContainsTextAndIsNonDigital_thenReturnNull() {
        when(dwpAddressLookupService.getDwpMappingByOffice(null, "My PIP Office 4")).thenReturn(Optional.of(OfficeMapping.builder().code("4").build()));
        String result = caseDataHelper.getCreatedInGapsFromField(Appeal.builder().mrnDetails(MrnDetails.builder().dwpIssuingOffice("My PIP Office 4").build()).build());

        assertNull(result);
    }

    @Test
    public void givenNoMrnDetails_thenPopulateDefaultIssuingOfficeAndRegionalCenter() {
        Map<String, Object> appealData = new HashMap<>();
        Appeal appeal = Appeal.builder().benefitType(BenefitType.builder().code("PIP").build()).build();
        when(dwpAddressLookupService.getDefaultDwpMappingByOffice("PIP")).thenReturn(Optional.of(OfficeMapping.builder().isDefault(true).mapping(Mapping.builder().ccd("DWP PIP (1)").build()).build()));
        when(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice("PIP","DWP PIP (1)")).thenReturn("DWP PIP (1)");
        caseDataHelper.addSscsDataToMap(appealData, appeal, null, null);
        assertEquals("DWP PIP (1)", appeal.getMrnDetails().getDwpIssuingOffice());
        assertEquals("DWP PIP (1)", appealData.get("dwpRegionalCentre"));
    }

    @Test
    public void givenMrnDetailsWithNoDwpIssuingOffice_thenPopulateDefaultIssuingOfficeAndRegionalCenter() {
        Map<String, Object> appealData = new HashMap<>();
        Appeal appeal = Appeal.builder().benefitType(BenefitType.builder().code("PIP").build()).mrnDetails(MrnDetails.builder().build()).build();
        when(dwpAddressLookupService.getDefaultDwpMappingByOffice("PIP")).thenReturn(Optional.of(OfficeMapping.builder().isDefault(true).mapping(Mapping.builder().ccd("DWP PIP (1)").build()).build()));
        when(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice("PIP","DWP PIP (1)")).thenReturn("DWP PIP (1)");
        caseDataHelper.addSscsDataToMap(appealData, appeal, null, null);
        assertEquals("DWP PIP (1)", appeal.getMrnDetails().getDwpIssuingOffice());
        assertEquals("DWP PIP (1)", appealData.get("dwpRegionalCentre"));
    }

    @Test
    public void givenMrnDetailsWithDwpIssuingOffice_thenPopulateRegionalCenter() {
        Map<String, Object> appealData = new HashMap<>();
        Appeal appeal = Appeal.builder().benefitType(BenefitType.builder().code("PIP").build()).mrnDetails(MrnDetails.builder().dwpIssuingOffice("DWP PIP (3)").build()).build();
        when(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice("PIP","DWP PIP (3)")).thenReturn("DWP PIP (3)");
        caseDataHelper.addSscsDataToMap(appealData, appeal, null, null);
        assertEquals("DWP PIP (3)", appeal.getMrnDetails().getDwpIssuingOffice());
        assertEquals("DWP PIP (3)", appealData.get("dwpRegionalCentre"));
    }
}
