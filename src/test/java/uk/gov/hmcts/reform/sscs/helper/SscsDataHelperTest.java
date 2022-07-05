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

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Mock
    private DwpAddressLookupService dwpAddressLookupService;

    @Mock
    private AirLookupService airLookupService;

    @Mock
    private PostcodeValidator postcodeValidator;

    private SscsDataHelper caseDataHelper;

    @Before
    public void setUp() {
        caseDataHelper = new SscsDataHelper(new CaseEvent(
            "appealCreated",
            "validAppealCreated",
            "incompleteApplicationReceived",
            "nonCompliant"),
            dwpAddressLookupService,
            airLookupService,
            postcodeValidator,
            true);
    }

    @Test
    public void givenACaseResponseWithWarnings_thenReturnIncompleteCaseEvent() {
        LocalDate localDate = LocalDate.now();

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        List<String> warnings = new ArrayList<>();
        warnings.add("Warnings");
        assertEquals("incompleteApplicationReceived",
            caseDataHelper.findEventToCreateCase(CaseResponse.builder()
                .transformedCase(transformedCase)
                .warnings(warnings)
                .build()));
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

    @Test
    public void givenAppellantRequestsConfidentialityOnSscs2_thenIsConfidentialCase() {
        Map<String, Object> transformedCase = new HashMap<>();
        Appeal appeal = Appeal.builder().benefitType(BenefitType.builder().code("childSupport").build()).appellant(Appellant.builder().confidentialityRequired(YesNo.YES).build()).build();

        caseDataHelper.addSscsDataToMap(transformedCase, appeal, null, null, FormType.SSCS2, "", null);

        assertEquals("Yes", transformedCase.get("isConfidentialCase"));
    }

    @Test
    public void givenAppellantRequestsConfidentialityOnSscs5_thenIsConfidentialCase() {
        Map<String, Object> transformedCase = new HashMap<>();
        Appeal appeal = Appeal.builder().benefitType(BenefitType.builder().code("taxCredit").build()).appellant(Appellant.builder().confidentialityRequired(YesNo.YES).build()).build();

        caseDataHelper.addSscsDataToMap(transformedCase, appeal, null, null, FormType.SSCS5, "", null);

        assertEquals("Yes", transformedCase.get("isConfidentialCase"));
    }

    @Test
    public void givenAppellantDoesNotRequestConfidentialityOnSscs2_thenIsConfidentialCase() {
        Map<String, Object> transformedCase = new HashMap<>();
        Appeal appeal = Appeal.builder().benefitType(BenefitType.builder().code("childSupport").build()).appellant(Appellant.builder().confidentialityRequired(YesNo.NO).build()).build();

        caseDataHelper.addSscsDataToMap(transformedCase, appeal, null, null, FormType.SSCS2, "", null);

        assertNull(transformedCase.get("isConfidentialCase"));
    }

    @Test
    public void givenAppellantRequestsConfidentialityOnSscs1_thenIsNotConfidentialCase() {
        Map<String, Object> transformedCase = new HashMap<>();
        Appeal appeal = Appeal.builder().benefitType(BenefitType.builder().code("childSupport").build()).appellant(Appellant.builder().confidentialityRequired(YesNo.YES).build()).build();

        caseDataHelper.addSscsDataToMap(transformedCase, appeal, null, null, FormType.SSCS1PEU, "", null);

        assertNull(transformedCase.get("isConfidentialCase"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void givenOtherPartiesAndChildMaintenanceOnSscs2_thenSetOtherPartiesAndChildMaintenanceOnCase() {
        Map<String, Object> transformedCase = new HashMap<>();
        Appeal appeal = Appeal.builder().benefitType(BenefitType.builder().code("benefit").build()).appellant(Appellant.builder().confidentialityRequired(YesNo.YES).build()).build();

        List<CcdValue<OtherParty>> otherParties =
            List.of(CcdValue.<OtherParty>builder().value(OtherParty.builder().id("other_party_1").build()).build());

        caseDataHelper.addSscsDataToMap(transformedCase, appeal, null, null, FormType.SSCS2, "123456", otherParties);

        OtherParty otherParty = ((List<CcdValue<OtherParty>>) transformedCase.get("otherParties")).get(0).getValue();
        assertEquals("other_party_1", otherParty.getId());
        assertEquals("123456", transformedCase.get("childMaintenanceNumber"));
    }

    @Test
    public void givenAppellantExistsAndCaseAccessManagementFeatureIsTrue_thenCaseAccessManagementFieldsAreSet() {
        Map<String, Object> transformedCase = new HashMap<>();
        Appeal appeal = Appeal.builder()
            .appellant(Appellant.builder().name(Name.builder().firstName("Harry").lastName("Potter").build()).build())
            .benefitType(BenefitType.builder().code("PIP").description("Personal Independence Payment").build())
            .build();

        caseDataHelper.addSscsDataToMap(transformedCase, appeal, null, null, FormType.SSCS1PEU, "", null);

        assertEquals("Harry Potter", transformedCase.get("caseNameHmctsInternal"));
        assertEquals("Harry Potter", transformedCase.get("caseNameHmctsRestricted"));
        assertEquals("Harry Potter", transformedCase.get("caseNamePublic"));
        assertEquals("personalIndependencePayment", transformedCase.get("CaseAccessCategory"));
        DynamicListItem caseManagementCategory = new DynamicListItem("PIP", "Personal Independence Payment");
        assertEquals(new DynamicList(caseManagementCategory, List.of(caseManagementCategory)), transformedCase.get("caseManagementCategory"));
        assertEquals("DWP", transformedCase.get("ogdType"));
    }

    @Test
    public void givenAppellantExistbeneftTypeBlankSscs1_thenUnknownValueSet() {
        Map<String, Object> transformedCase = new HashMap<>();
        Appeal appeal = Appeal.builder().appellant(Appellant.builder().name(Name.builder().firstName("Harry").lastName("Potter").build()).build())
            .benefitType(BenefitType.builder().code("").description("").build())
            .build();

        caseDataHelper.addSscsDataToMap(transformedCase, appeal, null, null, FormType.SSCS1PEU, "", null);

        assertEquals("Harry Potter", transformedCase.get("caseNameHmctsInternal"));
        assertEquals("Harry Potter", transformedCase.get("caseNameHmctsRestricted"));
        assertEquals("Harry Potter", transformedCase.get("caseNamePublic"));

        DynamicListItem caseManagementCategory = new DynamicListItem("sscs12Unknown", "SSCS1/2 Unknown");
        List<DynamicListItem> listItems = Arrays.asList(caseManagementCategory);
        assertEquals(new DynamicList(caseManagementCategory, listItems),
            transformedCase.get("caseManagementCategory"));
        assertEquals("DWP", transformedCase.get("ogdType"));
    }

    @Test
    public void givenAppellantExistbeneftTypeBlankSscs2_thenUnknownValueSet() {
        Map<String, Object> transformedCase = new HashMap<>();
        Appeal appeal = Appeal.builder().appellant(Appellant.builder().name(Name.builder().firstName("Harry").lastName("Potter").build()).build())
            .benefitType(BenefitType.builder().code("").description("").build())
            .build();

        caseDataHelper.addSscsDataToMap(transformedCase, appeal, null, null, FormType.SSCS2, "", null);

        assertEquals("Harry Potter", transformedCase.get("caseNameHmctsInternal"));
        assertEquals("Harry Potter", transformedCase.get("caseNameHmctsRestricted"));
        assertEquals("Harry Potter", transformedCase.get("caseNamePublic"));

        DynamicListItem caseManagementCategory = new DynamicListItem("sscs12Unknown", "SSCS1/2 Unknown");
        List<DynamicListItem> listItems = Arrays.asList(caseManagementCategory);
        assertEquals(new DynamicList(caseManagementCategory, listItems),
            transformedCase.get("caseManagementCategory"));
        assertEquals("DWP", transformedCase.get("ogdType"));
    }

    @Test
    public void givenAppellantExistbeneftTypeBlankSscs5_thenUnknownValueSet() {
        Map<String, Object> transformedCase = new HashMap<>();
        Appeal appeal = Appeal.builder().appellant(Appellant.builder().name(Name.builder().firstName("Harry").lastName("Potter").build()).build())
            .benefitType(BenefitType.builder().code("").description("").build())
            .build();

        caseDataHelper.addSscsDataToMap(transformedCase, appeal, null, null, FormType.SSCS5, "", null);

        assertEquals("Harry Potter", transformedCase.get("caseNameHmctsInternal"));
        assertEquals("Harry Potter", transformedCase.get("caseNameHmctsRestricted"));
        assertEquals("Harry Potter", transformedCase.get("caseNamePublic"));

        DynamicListItem caseManagementCategory = new DynamicListItem("sscs5Unknown", "SSCS5 Unknown");
        List<DynamicListItem> listItems = Arrays.asList(caseManagementCategory);
        assertEquals(new DynamicList(caseManagementCategory, listItems),
            transformedCase.get("caseManagementCategory"));
        assertEquals("HMRC", transformedCase.get("ogdType"));
    }
}
