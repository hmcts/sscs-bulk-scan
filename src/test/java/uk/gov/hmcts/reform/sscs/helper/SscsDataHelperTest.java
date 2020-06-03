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
}
