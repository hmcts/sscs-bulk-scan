package uk.gov.hmcts.reform.sscs.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.model.dwp.Mapping;
import uk.gov.hmcts.reform.sscs.model.dwp.OfficeMapping;
import uk.gov.hmcts.reform.sscs.service.AirLookupService;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.validators.PostcodeValidator;

@RunWith(SpringRunner.class)
public class SscsDataHelperTest {

    private SscsDataHelper caseDataHelper;

    @Mock
    private DwpAddressLookupService dwpAddressLookupService;

    @Mock
    private AirLookupService airLookupService;

    @Mock
    private PostcodeValidator postcodeValidator;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Before
    public void setUp() {
        caseDataHelper = new SscsDataHelper(new CaseEvent("appealCreated", "validAppealCreated", "incompleteApplicationReceived", "nonCompliant"), dwpAddressLookupService, airLookupService, postcodeValidator);
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
    public void givenAppellantAddressExist_thenReturnProcessingVenue() {
        when(postcodeValidator.isValid("CR2 8YY")).thenReturn(true);
        when(postcodeValidator.isValidPostcodeFormat("CR2 8YY")).thenReturn(true);
        when(airLookupService.lookupAirVenueNameByPostCode("CR2 8YY", BenefitType.builder().code("PIP").build())).thenReturn("Cardiff");
        String result = caseDataHelper.findProcessingVenue(Appellant.builder().address(Address.builder().postcode("CR2 8YY").build()).build(), BenefitType.builder().code("PIP").build());
        assertEquals("Cardiff", result);
    }

    @Test
    public void givenAppellantAndAppointeeAddressExist_thenReturnProcessingVenue() {
        when(postcodeValidator.isValid("CR2 8YY")).thenReturn(true);
        when(postcodeValidator.isValidPostcodeFormat("CR2 8YY")).thenReturn(true);
        when(airLookupService.lookupAirVenueNameByPostCode("CR2 8YY", BenefitType.builder().code("PIP").build())).thenReturn("Cardiff");
        String result = caseDataHelper.findProcessingVenue(Appellant.builder()
            .address(Address.builder().postcode("TS3 6NM").build())
            .appointee(Appointee.builder().address(Address.builder().postcode("CR2 8YY").build()).build())
            .build(), BenefitType.builder().code("PIP").build());
        assertEquals("Cardiff", result);
    }

    @Test
    public void givenASscs2CaseResponseWithChildMaintenance_thenReturnSscsDataMapSet() {
        Map<String, Object> transformedCase = new HashMap<>();
        caseDataHelper.addSscsDataToMap(transformedCase, null, null, null, FormType.SSCS2, "Test1234", null);
        assertEquals("Test1234", transformedCase.get("childMaintenanceNumber"));
    }

    @Test
    public void givenASscs1CaseResponseWithChildMaintenance_thenReturnSscsDataMapIgnoresValue() {
        Map<String, Object> transformedCase = new HashMap<>();
        caseDataHelper.addSscsDataToMap(transformedCase, null, null, null, FormType.SSCS1U, "Test1234", null);
        assertNull(transformedCase.get("childMaintenanceNumber"));
    }

    @Test
    public void givenCaseResponseWithNoMrnDetails_thenReturnDefaultDwpHandingOffice() {
        Map<String, Object> transformedCase = new HashMap<>();
        String expectedPipDwpHandingOffice = "Bellevale";
        Appeal appeal = Appeal.builder().benefitType(BenefitType.builder().code("PIP").build()).build();
        Optional<OfficeMapping> officeMapping = Optional.of(OfficeMapping.builder().isDefault(true).mapping(Mapping.builder().ccd("DWP PIP (3)").build()).build());
        when(dwpAddressLookupService.getDefaultDwpMappingByBenefitType(appeal.getBenefitType().getCode())).thenReturn(officeMapping);
        when(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice(appeal.getBenefitType().getCode(), officeMapping.get().getMapping().getCcd())).thenReturn(expectedPipDwpHandingOffice);

        caseDataHelper.addSscsDataToMap(transformedCase, appeal, null, null, FormType.SSCS1U, "", null);

        assertEquals(expectedPipDwpHandingOffice, transformedCase.get("dwpRegionalCentre"));
        verify(dwpAddressLookupService, times(1)).getDefaultDwpMappingByBenefitType(appeal.getBenefitType().getCode());
        verify(dwpAddressLookupService, times(1)).getDwpRegionalCenterByBenefitTypeAndOffice(appeal.getBenefitType().getCode(), officeMapping.get().getMapping().getCcd());
    }

    @Test
    public void givenCaseResponseWithMrnDetailsAndWithoutOffice_thenReturnDefaultDwpHandingOffice() {
        Map<String, Object> transformedCase = new HashMap<>();
        String expectedPipDwpHandingOffice = "Bellevale";
        Appeal appeal = Appeal.builder().benefitType(BenefitType.builder().code("PIP").build()).mrnDetails(MrnDetails.builder().dwpIssuingOffice(null).build()).build();
        Optional<OfficeMapping> officeMapping = Optional.of(OfficeMapping.builder().isDefault(true).mapping(Mapping.builder().ccd("DWP PIP (3)").build()).build());
        when(dwpAddressLookupService.getDefaultDwpMappingByBenefitType(appeal.getBenefitType().getCode())).thenReturn(officeMapping);
        when(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice(appeal.getBenefitType().getCode(), officeMapping.get().getMapping().getCcd())).thenReturn(expectedPipDwpHandingOffice);

        caseDataHelper.addSscsDataToMap(transformedCase, appeal, null, null, FormType.SSCS1U, "", null);

        assertEquals(expectedPipDwpHandingOffice, transformedCase.get("dwpRegionalCentre"));
        verify(dwpAddressLookupService, times(1)).getDefaultDwpMappingByBenefitType(appeal.getBenefitType().getCode());
        verify(dwpAddressLookupService, times(1)).getDwpRegionalCenterByBenefitTypeAndOffice(appeal.getBenefitType().getCode(), officeMapping.get().getMapping().getCcd());
    }

    @Test
    public void givenCaseResponseWithMrnDetailsAndWithOffice_thenReturnDwpHandingOffice() {
        Map<String, Object> transformedCase = new HashMap<>();
        String expectedPipDwpHandingOffice = "Newcastle";
        Appeal appeal = Appeal.builder().benefitType(BenefitType.builder().code("PIP").build()).mrnDetails(MrnDetails.builder().dwpIssuingOffice("DWP PIP (1)").build()).build();
        when(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice(appeal.getBenefitType().getCode(), appeal.getMrnDetails().getDwpIssuingOffice())).thenReturn(expectedPipDwpHandingOffice);

        caseDataHelper.addSscsDataToMap(transformedCase, appeal, null, null, FormType.SSCS1U, "", null);

        assertEquals(expectedPipDwpHandingOffice, transformedCase.get("dwpRegionalCentre"));
        verify(dwpAddressLookupService, times(1)).getDwpRegionalCenterByBenefitTypeAndOffice(appeal.getBenefitType().getCode(), appeal.getMrnDetails().getDwpIssuingOffice());
    }
}
