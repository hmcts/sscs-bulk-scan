package uk.gov.hmcts.reform.sscs.transformers;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.*;
import static uk.gov.hmcts.reform.sscs.ccd.domain.Benefit.ESA;
import static uk.gov.hmcts.reform.sscs.ccd.domain.Benefit.PIP;
import static uk.gov.hmcts.reform.sscs.ccd.domain.State.READY_TO_LIST;
import static uk.gov.hmcts.reform.sscs.ccd.domain.State.VALID_APPEAL;
import static uk.gov.hmcts.reform.sscs.ccd.service.SscsCcdConvertService.normaliseNino;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.assertj.core.api.Assertions;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedRecord;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;
import uk.gov.hmcts.reform.sscs.model.dwp.OfficeMapping;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.validators.SscsKeyValuePairValidator;

@RunWith(JUnitParamsRunner.class)
public class SscsCaseTransformerTest {

    @Mock
    SscsJsonExtractor sscsJsonExtractor;

    @Mock
    SscsKeyValuePairValidator keyValuePairValidator;

    @Mock
    DwpAddressLookupService dwpAddressLookupService;

    SscsDataHelper sscsDataHelper;

    @InjectMocks
    SscsCaseTransformer transformer;

    CaseDetails caseDetails;

    Map<String, Object> ocrMap = new HashMap<>();

    Map<String, Object> pairs = new HashMap<>();

    private List<String> offices;

    @Before
    public void setup() {
        initMocks(this);

        offices = new ArrayList<>();
        offices.add("1");
        offices.add("Balham DRT");

        sscsDataHelper = new SscsDataHelper(null, offices, dwpAddressLookupService);
        transformer = new SscsCaseTransformer(sscsJsonExtractor, keyValuePairValidator, sscsDataHelper);

        pairs.put("is_hearing_type_oral", IS_HEARING_TYPE_ORAL);
        pairs.put("is_hearing_type_paper", IS_HEARING_TYPE_PAPER);

        given(keyValuePairValidator.validate(ocrMap)).willReturn(CaseResponse.builder().build());
        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        caseDetails = CaseDetails.builder().caseData(ocrMap).build();
    }

    @Test
    @Parameters({"true", "false"})
    public void givenInvalidBenefitTypePairings_thenReturnAnError(boolean value) {
        pairs.put(IS_BENEFIT_TYPE_ESA, value);
        pairs.put(IS_BENEFIT_TYPE_PIP, value);
        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);
        assertFalse(result.getErrors().isEmpty());
        assertEquals("is_benefit_type_esa and is_benefit_type_pip have contradicting values", result.getErrors().get(0));
    }

    @Test
    @Parameters({"true", "false"})
    public void givenBenefitTypeIsDefinedWithTrueFalse_thenCheckCorrectCodeIsReturned(boolean isPip) {
        pairs.put(IS_BENEFIT_TYPE_PIP, isPip);
        pairs.put(IS_BENEFIT_TYPE_ESA, !isPip);
        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);
        assertTrue(result.getErrors().isEmpty());
        Appeal appeal = (Appeal) result.getTransformedCase().get("appeal");
        Benefit expectedBenefit = isPip ? PIP : ESA;
        assertEquals(expectedBenefit.name(),  appeal.getBenefitType().getCode());
    }

    @Test
    @Parameters({"Yes", "No"})
    public void givenBenefitTypeIsDefinedWithYesNo_thenCheckCorrectCodeIsReturned(String isPip) {
        pairs.put(IS_BENEFIT_TYPE_PIP, isPip);
        pairs.put(IS_BENEFIT_TYPE_ESA, isPip.equals("Yes") ? "No" : "Yes");
        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);
        assertTrue(result.getErrors().isEmpty());
        Appeal appeal = (Appeal) result.getTransformedCase().get("appeal");
        Benefit expectedBenefit = isPip.equals("Yes") ? PIP : ESA;
        assertEquals(expectedBenefit.name(),  appeal.getBenefitType().getCode());
    }

    @Test
    public void benefitTypeIsDefinedByDescriptionFieldWhenIsEsaOrIsPipIsNotSet() {
        pairs.put("benefit_type_description", BENEFIT_TYPE);
        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);
        assertTrue(result.getErrors().isEmpty());
        Appeal appeal = (Appeal) result.getTransformedCase().get("appeal");
        assertEquals(BENEFIT_TYPE,  appeal.getBenefitType().getCode());
    }

    @Test
    @Parameters({"person1", "person2", "representative"})
    public void canHandleAddressWithoutAddressLine4(String personType) {
        Address expectedAddress = Address.builder()
            .line1("10 my street")
            .town("town")
            .county("county")
            .postcode(APPELLANT_POSTCODE)
            .build();
        for (String person : Arrays.asList("person1", personType)) {
            pairs.put(person + "_address_line1", expectedAddress.getLine1());
            pairs.put(person + "_address_line2", expectedAddress.getTown());
            pairs.put(person + "_address_line3", expectedAddress.getCounty());
            pairs.put(person + "_postcode", expectedAddress.getPostcode());
        }

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        Appeal appeal = (Appeal) result.getTransformedCase().get("appeal");
        Address actual = personType.equals("representative") ? appeal.getRep().getAddress() :
            personType.equals("person2") ? appeal.getAppellant().getAppointee().getAddress() : appeal.getAppellant().getAddress();
        assertEquals(expectedAddress, actual);
    }

    @Test
    @Parameters({"Yes", "No"})
    public void willGenerateSubscriptionsWithEmailAndPhone(String subscribeSms) {

        pairs.put("person1_title", APPELLANT_TITLE);
        pairs.put("person1_first_name", APPELLANT_FIRST_NAME);
        pairs.put("person1_last_name", APPELLANT_LAST_NAME);
        pairs.put("person1_address_line1", APPELLANT_ADDRESS_LINE1);
        pairs.put("person1_address_line2", APPELLANT_ADDRESS_LINE2);
        pairs.put("person1_address_line3", APPELLANT_ADDRESS_LINE3);
        pairs.put("person1_address_line4", APPELLANT_ADDRESS_LINE4);
        pairs.put("person1_postcode", APPELLANT_POSTCODE);

        pairs.put("person1_want_sms_notifications", subscribeSms.equals("Yes"));
        pairs.put("person1_email", APPELLANT_EMAIL);
        pairs.put("person1_mobile", APPELLANT_MOBILE);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);
        Subscriptions subscriptions = (Subscriptions) result.getTransformedCase().get("subscriptions");

        Subscription expectedSubscription = Subscription.builder()
            .wantSmsNotifications(subscribeSms)
            .subscribeSms(subscribeSms)
            .mobile(APPELLANT_MOBILE)
            .email(APPELLANT_EMAIL)
            .tya(subscriptions.getAppellantSubscription().getTya())
            .build();

        assertEquals(expectedSubscription, subscriptions.getAppellantSubscription());
    }

    @Test
    public void givenKeyValuePairsWithPerson1AndPipBenefitType_thenBuildAnAppealWithAppellant() {
        given(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice(eq(BENEFIT_TYPE), eq(OFFICE))).willReturn(DWP_REGIONAL_CENTRE);
        pairs.put("benefit_type_description", BENEFIT_TYPE);
        pairs.put("mrn_date", MRN_DATE_VALUE);
        pairs.put("office", OFFICE);
        pairs.put("person1_title", APPELLANT_TITLE);
        pairs.put("person1_first_name", APPELLANT_FIRST_NAME);
        pairs.put("person1_last_name", APPELLANT_LAST_NAME);
        pairs.put("person1_address_line1", APPELLANT_ADDRESS_LINE1);
        pairs.put("person1_address_line2", APPELLANT_ADDRESS_LINE2);
        pairs.put("person1_address_line3", APPELLANT_ADDRESS_LINE3);
        pairs.put("person1_address_line4", APPELLANT_ADDRESS_LINE4);
        pairs.put("person1_postcode", APPELLANT_POSTCODE);
        pairs.put("person1_phone", APPELLANT_PHONE);
        pairs.put("person1_mobile", APPELLANT_MOBILE);
        pairs.put("person1_dob", APPELLANT_DATE_OF_BIRTH);
        pairs.put("person1_email", APPELLANT_EMAIL);
        pairs.put(APPEAL_GROUNDS, APPEAL_REASON);
        pairs.put("person1_nino", APPELLANT_NINO);
        pairs.put("representative_company", REPRESENTATIVE_NAME);
        pairs.put("representative_address_line1", REPRESENTATIVE_ADDRESS_LINE1);
        pairs.put("representative_address_line2", REPRESENTATIVE_ADDRESS_LINE2);
        pairs.put("representative_address_line3", REPRESENTATIVE_ADDRESS_LINE3);
        pairs.put("representative_address_line4", REPRESENTATIVE_ADDRESS_LINE4);
        pairs.put("representative_postcode", REPRESENTATIVE_POSTCODE);
        pairs.put("representative_phone", REPRESENTATIVE_PHONE_NUMBER);
        pairs.put("representative_email", REPRESENTATIVE_EMAIL);
        pairs.put("representative_title", REPRESENTATIVE_PERSON_TITLE);
        pairs.put("representative_first_name", REPRESENTATIVE_PERSON_FIRST_NAME);
        pairs.put("representative_last_name", REPRESENTATIVE_PERSON_LAST_NAME);
        pairs.put("appeal_late_reason", APPEAL_LATE_REASON);
        pairs.put("hearing_options_exclude_dates", HEARING_OPTIONS_EXCLUDE_DATES);
        pairs.put("hearing_options_hearing_loop", HEARING_LOOP);
        pairs.put("hearing_options_language_type", HEARING_OPTIONS_LANGUAGE_TYPE);
        pairs.put("agree_less_hearing_notice", AGREE_LESS_HEARING_NOTICE);
        pairs.put("signature_name", SIGNATURE_NAME);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(buildTestAppealData(), result.getTransformedCase().get("appeal"));
        assertEquals(BENEFIT_CODE, result.getTransformedCase().get("benefitCode"));
        assertEquals(ISSUE_CODE, result.getTransformedCase().get("issueCode"));
        assertEquals(CASE_CODE, result.getTransformedCase().get("caseCode"));
        assertEquals(DWP_REGIONAL_CENTRE, result.getTransformedCase().get("dwpRegionalCentre"));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenKeyValuePairsWithEsaBenefitType_thenBuildAnAppealWithAppellant() {
        given(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice(eq("ESA"), eq("Balham DRT"))).willReturn("Balham");
        pairs.put("benefit_type_description", "ESA");
        pairs.put("office", "Balham DRT");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("Balham", result.getTransformedCase().get("dwpRegionalCentre"));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenKeyValuePairsWithPerson2AndPerson1_thenBuildAnAppealWithAppellantAndAppointee() {

        pairs.put("person1_title", APPOINTEE_TITLE);
        pairs.put("person1_first_name", APPOINTEE_FIRST_NAME);
        pairs.put("person1_last_name", APPOINTEE_LAST_NAME);
        pairs.put("person1_address_line1", APPOINTEE_ADDRESS_LINE1);
        pairs.put("person1_address_line2", APPOINTEE_ADDRESS_LINE2);
        pairs.put("person1_address_line3", APPOINTEE_ADDRESS_LINE3);
        pairs.put("person1_address_line4", APPOINTEE_ADDRESS_LINE4);
        pairs.put("person1_postcode", APPOINTEE_POSTCODE);
        pairs.put("person1_phone", APPOINTEE_PHONE);
        pairs.put("person1_mobile", APPOINTEE_MOBILE);
        pairs.put("person1_dob", APPOINTEE_DATE_OF_BIRTH);
        pairs.put("person2_title", APPELLANT_TITLE);
        pairs.put("person2_first_name", APPELLANT_FIRST_NAME);
        pairs.put("person2_last_name", APPELLANT_LAST_NAME);
        pairs.put("person2_address_line1", APPELLANT_ADDRESS_LINE1);
        pairs.put("person2_address_line2", APPELLANT_ADDRESS_LINE2);
        pairs.put("person2_address_line3", APPELLANT_ADDRESS_LINE3);
        pairs.put("person2_address_line4", APPELLANT_ADDRESS_LINE4);
        pairs.put("person2_postcode", APPELLANT_POSTCODE);
        pairs.put("person2_dob", APPELLANT_DATE_OF_BIRTH);
        pairs.put("person2_nino", APPELLANT_NINO);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        Name appellantName = Name.builder().title(APPELLANT_TITLE).firstName(APPELLANT_FIRST_NAME).lastName(APPELLANT_LAST_NAME).build();
        Address appellantAddress = Address.builder().line1(APPELLANT_ADDRESS_LINE1).line2(APPELLANT_ADDRESS_LINE2).town(APPELLANT_ADDRESS_LINE3).county(APPELLANT_ADDRESS_LINE4).postcode(APPELLANT_POSTCODE).build();
        Identity appellantIdentity = Identity.builder().nino(normaliseNino(APPELLANT_NINO)).dob("1987-08-12").build();

        Name appointeeName = Name.builder().title(APPOINTEE_TITLE).firstName(APPOINTEE_FIRST_NAME).lastName(APPOINTEE_LAST_NAME).build();
        Address appointeeAddress = Address.builder().line1(APPOINTEE_ADDRESS_LINE1).line2(APPOINTEE_ADDRESS_LINE2).town(APPOINTEE_ADDRESS_LINE3).county(APPOINTEE_ADDRESS_LINE4).postcode(APPOINTEE_POSTCODE).build();
        Identity appointeeIdentity = Identity.builder().dob("1990-12-03").build();
        Contact appointeeContact = Contact.builder().phone(APPOINTEE_PHONE).mobile(APPOINTEE_MOBILE).build();
        Appointee appointee = Appointee.builder().name(appointeeName).address(appointeeAddress).contact(appointeeContact).identity(appointeeIdentity).build();

        Appellant expectedAppellant = Appellant.builder().name(appellantName).identity(appellantIdentity).isAppointee("Yes").address(appellantAddress).appointee(appointee).contact(Contact.builder().build()).build();

        Appellant appellantResult = ((Appeal) result.getTransformedCase().get("appeal")).getAppellant();
        assertEquals(expectedAppellant, appellantResult);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenKeyValuePairsWithPerson2AndNoPerson1_thenBuildAnAppealWithAppellant() {

        pairs.put("person2_title", APPELLANT_TITLE);
        pairs.put("person2_first_name", APPELLANT_FIRST_NAME);
        pairs.put("person2_last_name", APPELLANT_LAST_NAME);
        pairs.put("person2_address_line1", APPELLANT_ADDRESS_LINE1);
        pairs.put("person2_address_line2", APPELLANT_ADDRESS_LINE2);
        pairs.put("person2_address_line3", APPELLANT_ADDRESS_LINE3);
        pairs.put("person2_address_line4", APPELLANT_ADDRESS_LINE4);
        pairs.put("person2_postcode", APPELLANT_POSTCODE);
        pairs.put("person2_dob", APPELLANT_DATE_OF_BIRTH);
        pairs.put("person2_nino", APPELLANT_NINO);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        Name appellantName = Name.builder().title(APPELLANT_TITLE).firstName(APPELLANT_FIRST_NAME).lastName(APPELLANT_LAST_NAME).build();
        Address appellantAddress = Address.builder().line1(APPELLANT_ADDRESS_LINE1).line2(APPELLANT_ADDRESS_LINE2).town(APPELLANT_ADDRESS_LINE3).county(APPELLANT_ADDRESS_LINE4).postcode(APPELLANT_POSTCODE).build();
        Identity appellantIdentity = Identity.builder().nino(normaliseNino(APPELLANT_NINO)).dob("1987-08-12").build();

        Appellant expectedAppellant = Appellant.builder().name(appellantName).identity(appellantIdentity).isAppointee("No").address(appellantAddress).contact(Contact.builder().build()).build();

        Appellant appellantResult = ((Appeal) result.getTransformedCase().get("appeal")).getAppellant();
        assertEquals(expectedAppellant, appellantResult);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenAnAppellant_thenAddAppealNumberToAppellantSubscription() {
        pairs.put("person1_first_name", "Jeff");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        Subscriptions subscriptions = ((Subscriptions) result.getTransformedCase().get("subscriptions"));
        assertNotNull(subscriptions.getAppellantSubscription().getTya());
        assertNull(subscriptions.getAppointeeSubscription());
    }

    @Test
    public void givenAnAppellantAndAppointee_thenOnlyAddAppealNumberToAppointeeSubscription() {
        pairs.put("person1_first_name", "Jeff");
        pairs.put("person2_first_name", "Terry");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        Subscriptions subscriptions = ((Subscriptions) result.getTransformedCase().get("subscriptions"));
        assertNull(subscriptions.getAppellantSubscription());
        assertNotNull(subscriptions.getAppointeeSubscription().getTya());
    }

    @Test
    public void givenARepresentative_thenAddAppealNumberToRepresentativeSubscription() {
        pairs.put("representative_first_name", "Wendy");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        Subscriptions subscriptions = ((Subscriptions) result.getTransformedCase().get("subscriptions"));
        assertNotNull(subscriptions.getRepresentativeSubscription().getTya());
    }

    @Test
    public void givenOralHearingType_thenBuildAnAppealWithWantsToAttendYes() {

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(HEARING_TYPE_ORAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingType());
        assertEquals(YES_LITERAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getWantsToAttend());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenPaperHearingType_thenBuildAnAppealWithWantsToAttendNo() {

        pairs.put(IS_HEARING_TYPE_ORAL_LITERAL, false);
        pairs.put(IS_HEARING_TYPE_PAPER_LITERAL, true);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(HEARING_TYPE_PAPER, ((Appeal) result.getTransformedCase().get("appeal")).getHearingType());
        assertEquals(NO_LITERAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getWantsToAttend());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"Yes", "No"})
    public void givenHearingTypeYesNo_thenCorrectlyBuildAnAppealWithWantsToAttendValue(String isOral) {

        pairs.put(IS_HEARING_TYPE_ORAL_LITERAL, isOral);
        pairs.put(IS_HEARING_TYPE_PAPER_LITERAL, isOral.equals("Yes") ? "No" : "Yes");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        String expectedHearingType = isOral.equals("Yes") ? HEARING_TYPE_ORAL : HEARING_TYPE_PAPER;
        String attendingHearing = isOral.equals("Yes") ? YES_LITERAL : NO_LITERAL;

        assertEquals(expectedHearingType, ((Appeal) result.getTransformedCase().get("appeal")).getHearingType());
        assertEquals(attendingHearing, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getWantsToAttend());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenContradictingPaperAndOralCaseValues_thenAddErrorToList() {
        Map<String, Object> contradictingPairs = ImmutableMap.<String, Object>builder()
            .put(IS_HEARING_TYPE_ORAL_LITERAL, "true")
            .put(IS_HEARING_TYPE_PAPER_LITERAL, "true").build();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(contradictingPairs).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().contains("is_hearing_type_oral and is_hearing_type_paper have contradicting values"));
    }

    @Test
    public void givenHearingTypeOralIsTrueAndHearingTypePaperIsEmpty_thenSetHearingTypeToOral() {
        Map<String, Object> hearingTypePairs = new HashMap<>();
        hearingTypePairs.put("is_hearing_type_oral", true);
        hearingTypePairs.put("is_hearing_type_paper", "null");

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(hearingTypePairs).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(HEARING_TYPE_ORAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingType());
    }

    @Test
    public void givenHearingTypePaperIsTrueAndHearingTypeOralIsEmpty_thenSetHearingTypeToPaper() {
        Map<String, Object> hearingTypePairs = new HashMap<>();
        hearingTypePairs.put("is_hearing_type_oral", "null");
        hearingTypePairs.put("is_hearing_type_paper", true);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(hearingTypePairs).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(HEARING_TYPE_PAPER, ((Appeal) result.getTransformedCase().get("appeal")).getHearingType());
    }

    @Test
    public void givenHearingTypeOralIsFalseAndHearingTypePaperIsEmpty_thenSetHearingTypeToPaper() {
        Map<String, Object> hearingTypePairs = new HashMap<>();
        hearingTypePairs.put("is_hearing_type_oral", false);
        hearingTypePairs.put("is_hearing_type_paper", "null");

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(hearingTypePairs).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(HEARING_TYPE_PAPER, ((Appeal) result.getTransformedCase().get("appeal")).getHearingType());
    }

    @Test
    public void givenHearingTypePaperIsFalseAndHearingTypeOralIsEmpty_thenSetHearingTypeToOral() {
        Map<String, Object> hearingTypePairs = new HashMap<>();
        hearingTypePairs.put("is_hearing_type_oral", "null");
        hearingTypePairs.put("is_hearing_type_paper", false);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(hearingTypePairs).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(HEARING_TYPE_ORAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingType());
    }

    @Test
    public void givenBooleanValueIsRandomText_thenSetHearingTypeToNull() {
        Map<String, Object> textBooleanValueMap = ImmutableMap.<String, Object>builder()
            .put("is_hearing_type_oral", "I am a text value")
            .put("is_hearing_type_paper", "true").build();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(textBooleanValueMap).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertNull(((Appeal) result.getTransformedCase().get("appeal")).getHearingType());
    }

    @Test
    public void givenAnInvalidDateOfBirth_thenAddErrorToList() {
        pairs.put("person1_dob", "12/99/1987");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().contains("person1_dob is an invalid date field. Needs to be a valid date and in the format dd/mm/yyyy"));
    }

    @Test
    public void givenAnInvalidMrnDate_thenAddErrorToList() {
        pairs.put("mrn_date", "12/99/1987");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().contains("mrn_date is an invalid date field. Needs to be a valid date and in the format dd/mm/yyyy"));
    }

    @Test
    public void givenANullMrnDate_thenAddErrorToList() {
        pairs.put("mrn_date", null);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenAnEmptyStringMrnDate_thenAddErrorToList() {
        pairs.put("mrn_date", "");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenCaseContainsHearingOptions_thenBuildAnAppealWithSupport() {

        pairs.put("hearing_options_hearing_loop", HEARING_LOOP);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("hearingLoop", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));
        assertEquals("Yes", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getWantsSupport());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenCaseContainsNoHearingOptions_thenBuildAnAppealWithNoSupport() {

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("No", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getWantsSupport());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"true", "Yes"})
    public void givenHearingLoopIsRequired_thenBuildAnAppealWithArrangementsWithHearingLoop(String hearingLoop) {

        pairs.put("hearing_options_hearing_loop", hearingLoop);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("hearingLoop", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"false", "No"})
    public void givenHearingLoopIsNotRequired_thenBuildAnAppealWithNoHearingLoop(String hearingLoop) {

        pairs.put("hearing_options_hearing_loop", hearingLoop);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(0, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().size());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"true", "Yes"})
    public void givenDisabledAccessIsRequired_thenBuildAnAppealWithArrangementsWithDisabledAccess(String disabledAccess) {

        pairs.put("hearing_options_accessible_hearing_rooms", disabledAccess);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("disabledAccess", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"false", "No"})
    public void givenDisabledAccessIsNotRequired_thenBuildAnAppealWithNoDisabledAccess(String disabledAccess) {

        pairs.put("hearing_options_accessible_hearing_rooms", disabledAccess);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(0, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().size());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenSingleExcludedDate_thenBuildAnAppealWithExcludedStartDateAndScheduleHearingYes() {

        pairs.put("hearing_options_exclude_dates", HEARING_OPTIONS_EXCLUDE_DATES);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("2030-12-01", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates().get(0).getValue().getStart());
        assertEquals(YES_LITERAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getScheduleHearing());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenNoExcludedDate_thenBuildAnAppealWithExcludedStartDateAndScheduleHearingNo() {

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertNull(((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates());
        assertEquals(NO_LITERAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getScheduleHearing());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenAnExcludedDateAndDoesNotWantToAttendHearing_thenBuildAnAppealWithScheduleHearingNo() {

        pairs.put("hearing_options_exclude_dates", HEARING_OPTIONS_EXCLUDE_DATES);
        pairs.put("is_hearing_type_oral", false);
        pairs.put("is_hearing_type_paper", true);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("2030-12-01", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates().get(0).getValue().getStart());
        assertEquals(NO_LITERAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getScheduleHearing());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenTwoSingleExcludedDatesWithSpace_thenBuildAnAppealWithTwoExcludedStartDates() {

        pairs.put("hearing_options_exclude_dates", "12/12/2018, 16/12/2018");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        List<ExcludeDate> excludeDates = ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates();
        assertEquals("2018-12-12", (excludeDates.get(0).getValue().getStart()));
        assertEquals("2018-12-16", (excludeDates.get(1).getValue().getStart()));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenTwoSingleExcludedDatesWithNoSpace_thenBuildAnAppealWithTwoExcludedStartDates() {

        pairs.put("hearing_options_exclude_dates", "12/12/2018,16/12/2018");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        List<ExcludeDate> excludeDates = ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates();
        assertEquals("2018-12-12", (excludeDates.get(0).getValue().getStart()));
        assertEquals("2018-12-16", (excludeDates.get(1).getValue().getStart()));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenExcludedDateRangeIsEmpty_thenBuildAnAppealWithEmptyExcludedDateRange() {

        pairs.put("hearing_options_exclude_dates", "");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        List<ExcludeDate> excludeDates = ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates();

        assertNull(excludeDates);
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenExcludedDateRangeIsNull_thenBuildAnAppealWithEmptyExcludedDateRange() {

        pairs.put("hearing_options_exclude_dates", null);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        List<ExcludeDate> excludeDates = ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates();

        assertNull(excludeDates);
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenSingleExcludedDateFollowedByRangeWithSpace_thenBuildAnAppealWithSingleExcludedStartDateAndADateRange() {

        pairs.put("hearing_options_exclude_dates", "12/12/2018, 16/12/2018 - 18/12/2018");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        List<ExcludeDate> excludeDates = ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates();
        assertEquals("2018-12-12", (excludeDates.get(0).getValue().getStart()));
        assertEquals("2018-12-16", (excludeDates.get(1).getValue().getStart()));
        assertEquals("2018-12-18", (excludeDates.get(1).getValue().getEnd()));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenSingleExcludedDateFollowedByRangeWithNoSpace_thenBuildAnAppealWithSingleExcludedStartDateAndADateRange() {

        pairs.put("hearing_options_exclude_dates", "16/12/2018-18/12/2018");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        List<ExcludeDate> excludeDates = ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates();
        assertEquals("2018-12-16", (excludeDates.get(0).getValue().getStart()));
        assertEquals("2018-12-18", (excludeDates.get(0).getValue().getEnd()));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenMultipleExcludedDateFollowedByMultipleRange_thenBuildAnAppealWithMultipleExcludedStartDatesAndMultipleDateRanges() {

        pairs.put("hearing_options_exclude_dates", "12/12/2018, 14/12/2018, 16/12/2018 - 18/12/2018");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        List<ExcludeDate> excludeDates = ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates();
        assertEquals("2018-12-12", (excludeDates.get(0).getValue().getStart()));
        assertEquals("2018-12-14", (excludeDates.get(1).getValue().getStart()));
        assertEquals("2018-12-16", (excludeDates.get(2).getValue().getStart()));
        assertEquals("2018-12-18", (excludeDates.get(2).getValue().getEnd()));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenIncorrectExcludedDateFormat_thenAddAnError() {

        pairs.put("hearing_options_exclude_dates", "16th December 2018");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().contains("hearing_options_exclude_dates contains an invalid date range. Should be single dates separated by commas and/or a date range e.g. 01/01/2020, 07/01/2020, 12/01/2020 - 15/01/2020"));
    }

    @Test
    public void givenIncorrectExcludedDateRangeFormat_thenAddAnError() {

        pairs.put("hearing_options_exclude_dates", "16/12/2018 - 18/12/2018 - 20/12/2018");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().contains("hearing_options_exclude_dates contains an invalid date range. Should be single dates separated by commas and/or a date range e.g. 01/01/2020, 07/01/2020, 12/01/2020 - 15/01/2020"));
    }

    @Test
    public void givenALanguageTypeIsEntered_thenBuildAnAppealWithArrangementsWithLanguageInterpreterAndTypeSet() {

        pairs.put(HEARING_OPTIONS_LANGUAGE_TYPE_LITERAL, HEARING_OPTIONS_LANGUAGE_TYPE);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(HEARING_OPTIONS_LANGUAGE_TYPE, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getLanguages());
        assertEquals(YES_LITERAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getLanguageInterpreter());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenALanguageTypeAndDialectIsEntered_thenBuildAnAppealWithArrangementsWithLanguageInterpreterAndDialectAppended() {

        pairs.put(HEARING_OPTIONS_LANGUAGE_TYPE_LITERAL, HEARING_OPTIONS_LANGUAGE_TYPE);
        pairs.put(HEARING_OPTIONS_DIALECT_LITERAL, HEARING_OPTIONS_DIALECT_TYPE);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(HEARING_OPTIONS_LANGUAGE_TYPE + " " + HEARING_OPTIONS_DIALECT_TYPE, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getLanguages());
        assertEquals(YES_LITERAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getLanguageInterpreter());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenADialectIsEntered_thenBuildAnAppealWithArrangementsWithLanguageTypeSetToDialect() {

        pairs.put(HEARING_OPTIONS_DIALECT_LITERAL, HEARING_OPTIONS_DIALECT_TYPE);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(HEARING_OPTIONS_DIALECT_TYPE, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getLanguages());
        assertEquals(YES_LITERAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getLanguageInterpreter());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"Yes", "true"})
    public void givenASignLanguageInterpreterIsTrueAndTypeIsEntered_thenBuildAnAppealWithArrangementsWithSignLanguageInterpreterAndTypeSetToValueEntered(String signLanguageInterpreter) {

        pairs.put(HEARING_OPTIONS_SIGN_LANGUAGE_INTERPRETER_LITERAL, signLanguageInterpreter);
        pairs.put(HEARING_OPTIONS_SIGN_LANGUAGE_TYPE_LITERAL, SIGN_LANGUAGE_TYPE);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("signLanguageInterpreter", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));
        assertEquals(SIGN_LANGUAGE_TYPE, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getSignLanguageType());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenASignLanguageInterpreterIsTrueAndTypeIsNotEntered_thenBuildAnAppealWithArrangementsWithSignLanguageInterpreterAndTypeSetToDefaultType() {

        pairs.put(HEARING_OPTIONS_SIGN_LANGUAGE_INTERPRETER_LITERAL, SIGN_LANGUAGE_REQUIRED);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("signLanguageInterpreter", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));
        assertEquals(DEFAULT_SIGN_LANGUAGE, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getSignLanguageType());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"No", "false"})
    public void givenASignLanguageInterpreterIsFalse_thenBuildAnAppealWithNoArrangements(String signLanguageInterpreter) {

        pairs.put(HEARING_OPTIONS_SIGN_LANGUAGE_INTERPRETER_LITERAL, signLanguageInterpreter);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(0, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().size());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenASignLanguageInterpreterIsEntered_thenBuildAnAppealWithSignLanguageInterpreter() {

        pairs.put(HEARING_OPTIONS_SIGN_LANGUAGE_TYPE_LITERAL, SIGN_LANGUAGE_TYPE);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("signLanguageInterpreter", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));
        assertEquals(SIGN_LANGUAGE_TYPE, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getSignLanguageType());
        assertEquals("No", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getLanguageInterpreter());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenASignLanguageAndLanguageIsEntered_thenBuildAnAppealWithSignLanguageAndLanguageRequirements() {
        pairs.put(HEARING_OPTIONS_SIGN_LANGUAGE_TYPE_LITERAL, SIGN_LANGUAGE_TYPE);
        pairs.put(HEARING_OPTIONS_LANGUAGE_TYPE_LITERAL, HEARING_OPTIONS_LANGUAGE_TYPE);
        pairs.put(HEARING_OPTIONS_DIALECT_LITERAL, HEARING_OPTIONS_DIALECT_TYPE);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);
        assertEquals("signLanguageInterpreter", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));
        assertEquals(SIGN_LANGUAGE_TYPE, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getSignLanguageType());
        assertEquals(HEARING_OPTIONS_LANGUAGE_TYPE + " " + HEARING_OPTIONS_DIALECT_TYPE, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getLanguages());
    }

    @Test
    public void givenASignLanguageInterpreterAndLanguageInterpreterIsEntered_thenBuildAnAppealWithSignLanguageAndLanguageInterpreter() {
        pairs.put(IS_HEARING_TYPE_ORAL_LITERAL, true);
        pairs.put(IS_HEARING_TYPE_PAPER_LITERAL, false);
        pairs.put(HEARING_OPTIONS_LANGUAGE_TYPE_LITERAL, HEARING_OPTIONS_LANGUAGE_TYPE);
        pairs.put(HEARING_OPTIONS_DIALECT_LITERAL, HEARING_OPTIONS_DIALECT_TYPE);
        pairs.put(HEARING_OPTIONS_SIGN_LANGUAGE_INTERPRETER_LITERAL, true);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);
        assertEquals("signLanguageInterpreter", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));
        assertEquals("British Sign Language", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getSignLanguageType());
        assertEquals(HEARING_OPTIONS_LANGUAGE_TYPE + " " + HEARING_OPTIONS_DIALECT_TYPE, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getLanguages());
    }

    @Test
    public void givenACaseWithNullOcrData_thenAddErrorToList() {

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(null).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().contains("No OCR data, case cannot be created"));
    }

    @Test
    public void givenACaseWithNoOcrData_thenAddErrorToList() {
        Map<String, Object> noPairs = ImmutableMap.<String, Object>builder().build();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(noPairs).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().contains("No OCR data, case cannot be created"));
    }

    @Test
    public void givenACaseWithFailedSchemaValidation_thenAddErrorToList() {

        given(keyValuePairValidator.validate(ocrMap)).willReturn(CaseResponse.builder().errors(ImmutableList.of("NI Number is invalid")).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().contains("NI Number is invalid"));
    }

    @Test
    public void createCaseWithTodaysCaseCreationDate() {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String nowDateFormatted = df.format(new Date());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(nowDateFormatted, result.getTransformedCase().get("caseCreated"));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenOneDocument_thenBuildACase() {
        List<ScannedRecord> records = new ArrayList<>();
        ScannedRecord scannedRecord = buildTestScannedRecord(DocumentLink.builder().documentUrl("www.test.com").build(), "My subtype");
        records.add(scannedRecord);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).records(records).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        Map<String, Object> transformedCase = result.getTransformedCase();
        @SuppressWarnings("unchecked")
        List<SscsDocument> docs = ((List<SscsDocument>) transformedCase.get("sscsDocument"));
        assertEquals("2018-08-10", docs.get(0).getValue().getDocumentDateAdded());
        assertEquals(scannedRecord.getFileName(), docs.get(0).getValue().getDocumentFileName());
        assertEquals(scannedRecord.getUrl().getDocumentUrl(), docs.get(0).getValue().getDocumentLink().getDocumentUrl());
        assertEquals("appellantEvidence", docs.get(0).getValue().getDocumentType());

        assertEquals(YES_LITERAL, transformedCase.get("evidencePresent"));

        org.joda.time.format.DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd");
        String expectedCreatedDate = dtfOut.print(new DateTime());
        assertEquals(expectedCreatedDate, transformedCase.get("caseCreated"));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenOneDocumentWithBadlyFormedOpeningDate_thenBuildACase() {
        List<ScannedRecord> records = new ArrayList<>();
        ScannedRecord scannedRecord = buildTestScannedRecord(DocumentLink.builder().documentUrl("www.test.com").build(), "My subtype");
        records.add(scannedRecord);

        caseDetails.getCaseData().put("openingDate", "01-01-99");

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).records(records).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        Map<String, Object> transformedCase = result.getTransformedCase();
        @SuppressWarnings("unchecked")
        List<SscsDocument> docs = ((List<SscsDocument>) transformedCase.get("sscsDocument"));
        assertEquals("2018-08-10", docs.get(0).getValue().getDocumentDateAdded());
        assertEquals(scannedRecord.getFileName(), docs.get(0).getValue().getDocumentFileName());
        assertEquals(scannedRecord.getUrl().getDocumentUrl(), docs.get(0).getValue().getDocumentLink().getDocumentUrl());
        assertEquals("appellantEvidence", docs.get(0).getValue().getDocumentType());

        assertEquals(YES_LITERAL, transformedCase.get("evidencePresent"));

        org.joda.time.format.DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd");
        String expectedCreatedDate = dtfOut.print(new DateTime());
        assertEquals(expectedCreatedDate, transformedCase.get("caseCreated"));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenOneDocumentWithAnOpeningDate_thenBuildACase() {
        List<ScannedRecord> records = new ArrayList<>();
        ScannedRecord scannedRecord = buildTestScannedRecord(DocumentLink.builder().documentUrl("www.test.com").build(), "My subtype");
        records.add(scannedRecord);

        org.joda.time.format.DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd");
        String expectedCreatedDate = dtfOut.print(new DateTime().minusYears(3));

        caseDetails.getCaseData().put("openingDate", expectedCreatedDate);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).records(records).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        Map<String, Object> transformedCase = result.getTransformedCase();
        @SuppressWarnings("unchecked")
        List<SscsDocument> docs = ((List<SscsDocument>) transformedCase.get("sscsDocument"));
        assertEquals("2018-08-10", docs.get(0).getValue().getDocumentDateAdded());
        assertEquals(scannedRecord.getFileName(), docs.get(0).getValue().getDocumentFileName());
        assertEquals(scannedRecord.getUrl().getDocumentUrl(), docs.get(0).getValue().getDocumentLink().getDocumentUrl());
        assertEquals("appellantEvidence", docs.get(0).getValue().getDocumentType());

        assertEquals(YES_LITERAL, transformedCase.get("evidencePresent"));
        assertEquals(expectedCreatedDate, transformedCase.get("caseCreated"));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void should_handle_datetimes_with_and_without_milliseconds() {
        // given
        List<ScannedRecord> scannedRecords = Arrays.asList(
            ScannedRecord.builder()
                .scannedDate("2019-02-20T11:22:33") // no millis
                .controlNumber("123")
                .url(DocumentLink.builder().documentUrl("www.test.com").build())
                .fileName("mrn.jpg")
                .type("Testing")
                .subtype("My subtype").build(),
            ScannedRecord.builder()
                .scannedDate("2019-02-19T11:22:33.123") // with millis
                .controlNumber("567")
                .url(DocumentLink.builder().documentUrl("www.test.com").build())
                .fileName("mrn.jpg")
                .type("Testing")
                .subtype("My subtype").build()
        );
        given(sscsJsonExtractor.extractJson(ocrMap))
            .willReturn(ScannedData.builder().ocrCaseData(pairs).records(scannedRecords).build());

        // when
        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        // then
        @SuppressWarnings("unchecked")
        List<SscsDocument> docs = ((List<SscsDocument>) result.getTransformedCase().get("sscsDocument"));

        Assertions.assertThat(docs)
            .extracting(doc -> doc.getValue().getDocumentDateAdded())
            .containsExactlyInAnyOrder(
                "2019-02-20",
                "2019-02-19"
            );
    }

    @Test
    public void givenOneSscs1FormAndOneEvidence_thenBuildACaseWithCorrectDocumentTypes() {
        List<ScannedRecord> records = new ArrayList<>();
        ScannedRecord scannedRecord1 = buildTestScannedRecord(DocumentLink.builder().documentUrl("http://www.test1.com").build(), "SSCS1");
        ScannedRecord scannedRecord2 = buildTestScannedRecord(DocumentLink.builder().documentUrl("http://www.test2.com").build(), "My subtype");
        records.add(scannedRecord1);
        records.add(scannedRecord2);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).records(records).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        @SuppressWarnings("unchecked")
        List<SscsDocument> docs = ((List<SscsDocument>) result.getTransformedCase().get("sscsDocument"));
        assertEquals("2018-08-10", docs.get(0).getValue().getDocumentDateAdded());
        assertEquals(scannedRecord1.getFileName(), docs.get(0).getValue().getDocumentFileName());
        assertEquals(scannedRecord1.getUrl().getDocumentUrl(), docs.get(0).getValue().getDocumentLink().getDocumentUrl());
        assertEquals("sscs1", docs.get(0).getValue().getDocumentType());
        assertEquals("2018-08-10", docs.get(1).getValue().getDocumentDateAdded());
        assertEquals(scannedRecord2.getFileName(), docs.get(1).getValue().getDocumentFileName());
        assertEquals(scannedRecord2.getUrl().getDocumentUrl(), docs.get(1).getValue().getDocumentLink().getDocumentUrl());
        assertEquals("appellantEvidence", docs.get(1).getValue().getDocumentType());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenOneDocumentWithNoDetails_thenShowAnError() {
        List<ScannedRecord> records = new ArrayList<>();
        ScannedRecord scannedRecord = ScannedRecord.builder()
            .scannedDate(null)
            .controlNumber(null)
            .url(null)
            .fileName(null)
            .type(null)
            .subtype(null).build();

        records.add(scannedRecord);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).records(records).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().contains("File name field must not be empty"));
    }

    @Test
    public void givenOneDocumentWithNoFileExtension_thenShowAnError() {
        List<ScannedRecord> records = new ArrayList<>();

        ScannedRecord scannedRecord = ScannedRecord.builder()
            .scannedDate("2018-08-10T20:11:12.000")
            .controlNumber("123")
            .url(DocumentLink.builder().documentUrl("www.test.com").build())
            .fileName("mrn details")
            .type("Testing")
            .subtype("My subtype").build();
        records.add(scannedRecord);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).records(records).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().contains("Evidence file type 'mrn details' unknown"));
    }

    @Test
    public void givenOneDocumentWithInvalidFileExtension_thenShowAnError() {
        List<ScannedRecord> records = new ArrayList<>();

        ScannedRecord scannedRecord = ScannedRecord.builder()
            .scannedDate("2018-08-10T20:11:12.000")
            .controlNumber("123")
            .url(DocumentLink.builder().documentUrl("www.test.com").build())
            .fileName("mrn_details.xyz")
            .type("Testing")
            .subtype("My subtype").build();
        records.add(scannedRecord);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).records(records).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertTrue(result.getErrors().contains("Evidence file type 'xyz' unknown"));
    }

    @Test
    public void givenACaseWithNoDocuments_thenBuildACaseWithNoEvidencePresent() {
        List<ScannedRecord> records = new ArrayList<>();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).records(records).build());

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        Map<String, Object> transformedCase = result.getTransformedCase();
        assertEquals(NO_LITERAL, transformedCase.get("evidencePresent"));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenAnAppellantDateOfBirth_thenSetGeneratedDobField() {
        pairs.put("person1_dob", "12/01/1987");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        String generatedDob = ((String) result.getTransformedCase().get("generatedDOB"));
        assertEquals("1987-01-12", generatedDob);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenAnAppellantSurname_thenSetGeneratedSurnameField() {
        pairs.put("person1_last_name", "Smith");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        String generatedSurname = ((String) result.getTransformedCase().get("generatedSurname"));
        assertEquals("Smith", generatedSurname);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenAnAppellantNino_thenSetGeneratedNinoField() {
        pairs.put("person1_nino", "JT0123456B");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        String generatedNino = ((String) result.getTransformedCase().get("generatedNino"));
        assertEquals("JT0123456B", generatedNino);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenAPipCaseWithReadyToListOffice_thenSetCreatedInGapsFromFieldToReadyToList() {
        pairs.put("office", "1");
        pairs.put(IS_BENEFIT_TYPE_PIP, true);
        pairs.put(IS_BENEFIT_TYPE_ESA, false);

        when(dwpAddressLookupService.getDwpMappingByOffice("PIP", "1")).thenReturn(Optional.of(OfficeMapping.builder().code("1").build()));

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        String createdInGapsFrom = ((String) result.getTransformedCase().get("createdInGapsFrom"));
        assertEquals(READY_TO_LIST.getId(), createdInGapsFrom);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenAPipCaseWithValidAppealOffice_thenSetCreatedInGapsFromFieldToValidAppeal() {
        pairs.put("office", "2");
        pairs.put(IS_BENEFIT_TYPE_PIP, true);
        pairs.put(IS_BENEFIT_TYPE_ESA, false);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        String createdInGapsFrom = ((String) result.getTransformedCase().get("createdInGapsFrom"));
        assertEquals(VALID_APPEAL.getId(), createdInGapsFrom);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenAEsaCaseWithReadyToListOffice_thenSetCreatedInGapsFromFieldToReadyToList() {
        pairs.put("office", "Balham DRT");
        pairs.put(IS_BENEFIT_TYPE_PIP, false);
        pairs.put(IS_BENEFIT_TYPE_ESA, true);

        when(dwpAddressLookupService.getDwpMappingByOffice("ESA", "Balham DRT")).thenReturn(Optional.of(OfficeMapping.builder().code("Balham DRT").build()));

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        String createdInGapsFrom = ((String) result.getTransformedCase().get("createdInGapsFrom"));
        assertEquals(READY_TO_LIST.getId(), createdInGapsFrom);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenAEsaCaseWithValidAppealOffice_thenSetCreatedInGapsFromFieldToValidAppeal() {
        pairs.put("office", "Chesterfield DRT");
        pairs.put(IS_BENEFIT_TYPE_PIP, false);
        pairs.put(IS_BENEFIT_TYPE_ESA, true);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        String createdInGapsFrom = ((String) result.getTransformedCase().get("createdInGapsFrom"));
        assertEquals(VALID_APPEAL.getId(), createdInGapsFrom);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenACaseWithNoReadyToListOffice_thenSetCreatedInGapsFromFieldToNull() {
        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        String createdInGapsFrom = ((String) result.getTransformedCase().get("createdInGapsFrom"));
        assertNull(createdInGapsFrom);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"true", "Yes"})
    public void givenAgreeLessHearingNoticeIsRequired_thenBuildAnAppealWithAgreeLessHearingNotice(String agreeLessHearingNotice) {

        pairs.put("agree_less_hearing_notice", agreeLessHearingNotice);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("Yes", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getAgreeLessNotice());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"false", "No"})
    public void givenAgreeLessHearingNoticeIsNotRequired_thenBuildAnAppealWithNoAgreeLessHearingNotice(String agreeLessHearingNotice) {

        pairs.put("agree_less_hearing_notice", agreeLessHearingNotice);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("No", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getAgreeLessNotice());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"true", "Yes"})
    public void givenTellTribunalAboutDatesIsRequiredAndExcludedDatesProvided_thenBuildAnAppealWithExcludedDates(String tellTribunalAboutDates) {

        pairs.put("tell_tribunal_about_dates", tellTribunalAboutDates);
        pairs.put("hearing_options_exclude_dates", HEARING_OPTIONS_EXCLUDE_DATES);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals("2030-12-01", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates().get(0).getValue().getStart());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"true", "Yes"})
    public void givenTellTribunalAboutDatesIsRequiredAndExcludedDatesIsEmpty_thenProvideWarningToCaseworker(String tellTribunalAboutDates) {

        pairs.put("tell_tribunal_about_dates", tellTribunalAboutDates);
        pairs.put("hearing_options_exclude_dates", "");

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(1, result.getWarnings().size());
        assertEquals("No excluded dates provided but data indicates that there are dates customer cannot attend hearing as " + TELL_TRIBUNAL_ABOUT_DATES + " is true. Is this correct?", result.getWarnings().get(0));

        assertNull(((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"true", "Yes"})
    public void givenTellTribunalAboutDatesIsRequiredAndExcludedDatesIsNotPresent_thenProvideWarningToCaseworker(String tellTribunalAboutDates) {

        pairs.put("tell_tribunal_about_dates", tellTribunalAboutDates);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertEquals(1, result.getWarnings().size());
        assertEquals("No excluded dates provided but data indicates that there are dates customer cannot attend hearing as " + TELL_TRIBUNAL_ABOUT_DATES + " is true. Is this correct?", result.getWarnings().get(0));

        assertNull(((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @Parameters({"false", "No"})
    public void givenTellTribunalAboutDatesIsNotRequired_thenBuildAnAppealWithNoExcludedDates(String tellTribunalAboutDates) {

        pairs.put("tell_tribunal_about_dates", tellTribunalAboutDates);

        CaseResponse result = transformer.transformExceptionRecordToCase(caseDetails);

        assertNull(((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getExcludeDates());

        assertTrue(result.getErrors().isEmpty());
    }

    private Appeal buildTestAppealData() {
        Name appellantName = Name.builder().title(APPELLANT_TITLE).firstName(APPELLANT_FIRST_NAME).lastName(APPELLANT_LAST_NAME).build();
        Address appellantAddress = Address.builder().line1(APPELLANT_ADDRESS_LINE1).line2(APPELLANT_ADDRESS_LINE2).town(APPELLANT_ADDRESS_LINE3).county(APPELLANT_ADDRESS_LINE4).postcode(APPELLANT_POSTCODE).build();
        Identity appellantIdentity = Identity.builder().nino(normaliseNino(APPELLANT_NINO)).dob(formatDate(APPELLANT_DATE_OF_BIRTH)).build();
        Contact appellantContact = Contact.builder().phone(APPELLANT_PHONE).mobile(APPELLANT_MOBILE).email(APPELLANT_EMAIL).build();
        Appellant appellant = Appellant.builder().name(appellantName).identity(appellantIdentity).isAppointee("No").address(appellantAddress).contact(appellantContact).build();

        Name repName = Name.builder().title(REPRESENTATIVE_PERSON_TITLE).firstName(REPRESENTATIVE_PERSON_FIRST_NAME).lastName(REPRESENTATIVE_PERSON_LAST_NAME).build();
        Address repAddress = Address.builder().line1(REPRESENTATIVE_ADDRESS_LINE1).line2(REPRESENTATIVE_ADDRESS_LINE2).town(REPRESENTATIVE_ADDRESS_LINE3).county(REPRESENTATIVE_ADDRESS_LINE4).postcode(REPRESENTATIVE_POSTCODE).build();
        Contact repContact = Contact.builder().phone(REPRESENTATIVE_PHONE_NUMBER).email(REPRESENTATIVE_EMAIL).build();

        ExcludeDate excludeDate = ExcludeDate.builder().value(DateRange.builder().start(formatDate(HEARING_OPTIONS_EXCLUDE_DATES)).build()).build();
        List<ExcludeDate> excludedDates = new ArrayList<>();
        excludedDates.add(excludeDate);

        List<String> hearingSupportArrangements = new ArrayList<>();
        hearingSupportArrangements.add("hearingLoop");

        return Appeal.builder()
            .benefitType(BenefitType.builder().code(BENEFIT_TYPE).build())
            .appellant(appellant)
            .appealReasons(AppealReasons.builder().reasons(Collections.singletonList(AppealReason.builder().value(AppealReasonDetails.builder().description(APPEAL_REASON).build()).build())).build())
            .rep(Representative.builder().hasRepresentative(YES_LITERAL).name(repName).address(repAddress).contact(repContact).organisation(REPRESENTATIVE_NAME).build())
            .mrnDetails(MrnDetails.builder().mrnDate(formatDate(MRN_DATE_VALUE)).dwpIssuingOffice(OFFICE).mrnLateReason(APPEAL_LATE_REASON).build())
            .hearingType(HEARING_TYPE_ORAL)
            .hearingOptions(HearingOptions.builder()
                .scheduleHearing(YES_LITERAL)
                .excludeDates(excludedDates)
                .agreeLessNotice(YES_LITERAL)
                .arrangements(hearingSupportArrangements)
                .languageInterpreter(YES_LITERAL)
                .languages(HEARING_OPTIONS_LANGUAGE_TYPE)
                .wantsToAttend(YES_LITERAL)
                .wantsSupport(YES_LITERAL).build())
            .signer(SIGNATURE_NAME)
            .receivedVia("Paper")
            .build();
    }

    private ScannedRecord buildTestScannedRecord(DocumentLink link, String subType) {
        return ScannedRecord.builder()
            .scannedDate("2018-08-10T20:11:12.000")
            .controlNumber("123")
            .url(link)
            .fileName("mrn.jpg")
            .type("Form")
            .subtype(subType).build();
    }

    private String formatDate(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return LocalDate.parse(date, formatter).toString();
    }

}
