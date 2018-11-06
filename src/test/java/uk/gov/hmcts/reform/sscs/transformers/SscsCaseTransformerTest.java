package uk.gov.hmcts.reform.sscs.transformers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.*;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.*;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseValidationResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedRecord;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;

public class SscsCaseTransformerTest {

    @Mock
    SscsJsonExtractor sscsJsonExtractor;

    @InjectMocks
    SscsCaseTransformer transformer;

    Map<String, Object> ocrMap = new HashMap<>();

    Map<String, Object> pairs = new HashMap<>();

    @Before
    public void setup() {
        initMocks(this);
        pairs.put("is_hearing_type_oral", IS_HEARING_TYPE_ORAL);
        pairs.put("is_hearing_type_paper", IS_HEARING_TYPE_PAPER);
    }

    @Test
    public void givenKeyValuePairsWithPerson1_thenBuildAnAppealWithAppellant() {

        pairs.put("benefit_type_description", BENEFIT_TYPE_DESCRIPTION);
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
        pairs.put("person1_nino", APPELLANT_NINO);
        pairs.put("representative_company", REPRESENTATIVE_NAME);
        pairs.put("representative_address_line1", REPRESENTATIVE_ADDRESS_LINE1);
        pairs.put("representative_address_line2", REPRESENTATIVE_ADDRESS_LINE2);
        pairs.put("representative_address_line3", REPRESENTATIVE_ADDRESS_LINE3);
        pairs.put("representative_address_line4", REPRESENTATIVE_ADDRESS_LINE4);
        pairs.put("representative_postcode", REPRESENTATIVE_POSTCODE);
        pairs.put("representative_phone", REPRESENTATIVE_PHONE_NUMBER);
        pairs.put("representative_title", REPRESENTATIVE_PERSON_TITLE);
        pairs.put("representative_first_name", REPRESENTATIVE_PERSON_FIRST_NAME);
        pairs.put("representative_last_name", REPRESENTATIVE_PERSON_LAST_NAME);
        pairs.put("appeal_late_reason", APPEAL_LATE_REASON);
        pairs.put("hearing_options_exclude_dates", HEARING_OPTIONS_EXCLUDE_DATES);
        pairs.put("hearing_options_hearing_loop", HEARING_LOOP);
        pairs.put("hearing_options_language_type", HEARING_OPTIONS_LANGUAGE_TYPE);
        pairs.put("agree_less_hearing_notice", AGREE_LESS_HEARING_NOTICE);
        pairs.put("signature_name", SIGNATURE_NAME);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertEquals(buildTestAppealData(), result.getTransformedCase().get("appeal"));

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
        pairs.put("person1_nino", APPOINTEE_NINO);
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

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        Name appellantName = Name.builder().title(APPELLANT_TITLE).firstName(APPELLANT_FIRST_NAME).lastName(APPELLANT_LAST_NAME).build();
        Address appellantAddress = Address.builder().line1(APPELLANT_ADDRESS_LINE1).line2(APPELLANT_ADDRESS_LINE2).town(APPELLANT_ADDRESS_LINE3).county(APPELLANT_ADDRESS_LINE4).postcode(APPELLANT_POSTCODE).build();
        Identity appellantIdentity = Identity.builder().nino(APPELLANT_NINO).dob("1987-08-12").build();

        Name appointeeName = Name.builder().title(APPOINTEE_TITLE).firstName(APPOINTEE_FIRST_NAME).lastName(APPOINTEE_LAST_NAME).build();
        Address appointeeAddress = Address.builder().line1(APPOINTEE_ADDRESS_LINE1).line2(APPOINTEE_ADDRESS_LINE2).town(APPOINTEE_ADDRESS_LINE3).county(APPOINTEE_ADDRESS_LINE4).postcode(APPOINTEE_POSTCODE).build();
        Identity appointeeIdentity = Identity.builder().nino(APPOINTEE_NINO).dob("1990-12-03").build();
        Contact appointeeContact = Contact.builder().phone(APPOINTEE_PHONE).mobile(APPOINTEE_MOBILE).build();
        Appointee appointee = Appointee.builder().name(appointeeName).address(appointeeAddress).contact(appointeeContact).identity(appointeeIdentity).build();

        Appellant expectedAppellant = Appellant.builder().name(appellantName).identity(appellantIdentity).address(appellantAddress).appointee(appointee).build();

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

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        Name appellantName = Name.builder().title(APPELLANT_TITLE).firstName(APPELLANT_FIRST_NAME).lastName(APPELLANT_LAST_NAME).build();
        Address appellantAddress = Address.builder().line1(APPELLANT_ADDRESS_LINE1).line2(APPELLANT_ADDRESS_LINE2).town(APPELLANT_ADDRESS_LINE3).county(APPELLANT_ADDRESS_LINE4).postcode(APPELLANT_POSTCODE).build();
        Identity appellantIdentity = Identity.builder().nino(APPELLANT_NINO).dob("1987-08-12").build();

        Appellant expectedAppellant = Appellant.builder().name(appellantName).identity(appellantIdentity).address(appellantAddress).build();

        Appellant appellantResult = ((Appeal) result.getTransformedCase().get("appeal")).getAppellant();
        assertEquals(expectedAppellant, appellantResult);

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenOralHearingType_thenBuildAnAppealWithWantsToAttendYes() {

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertEquals(HEARING_TYPE_ORAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingType());
        assertEquals(YES_LITERAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getWantsToAttend());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenPaperHearingType_thenBuildAnAppealWithWantsToAttendNo() {

        pairs.put("is_hearing_type_oral", false);
        pairs.put("is_hearing_type_paper", true);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertEquals(HEARING_TYPE_PAPER, ((Appeal) result.getTransformedCase().get("appeal")).getHearingType());
        assertEquals(NO_LITERAL, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getWantsToAttend());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenContradictingPaperAndOralCaseValues_thenAddErrorToList() {
        Map<String, Object> contradictingPairs = ImmutableMap.<String, Object>builder()
            .put(IS_HEARING_TYPE_ORAL_LITERAL, "true")
            .put(IS_HEARING_TYPE_PAPER_LITERAL, "true").build();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(contradictingPairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertTrue(result.getErrors().contains("is_hearing_type_oral and is_hearing_type_paper have contradicting values"));
    }

    @Test
    public void givenBooleanValueIsText_thenAddErrorToList() {
        Map<String, Object> textBooleanValueMap = ImmutableMap.<String, Object>builder()
            .put("is_hearing_type_oral", "I am a text value")
            .put("is_hearing_type_paper", "true").build();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(textBooleanValueMap).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertTrue(result.getErrors().contains("is_hearing_type_oral does not contain a valid boolean value. Needs to be true or false"));
    }

    @Test
    public void givenAnInvalidDateOfBirth_thenAddErrorToList() {
        pairs.put("person1_dob", "12/99/1987");

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertTrue(result.getErrors().contains("person1_dob is an invalid date field. Needs to be in the format dd/mm/yyyy"));
    }

    @Test
    public void givenCaseContainsHearingOptions_thenBuildAnAppealWithSupport() {

        pairs.put("hearing_options_hearing_loop", HEARING_LOOP);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertEquals("hearingLoop", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));
        assertEquals("Yes", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getWantsSupport());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenCaseContainsNoHearingOptions_thenBuildAnAppealWithNoSupport() {

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertEquals("No", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getWantsSupport());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenHearingLoopIsTrue_thenBuildAnAppealWithArrangementsWithHearingLoop() {

        pairs.put("hearing_options_hearing_loop", HEARING_LOOP);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertEquals("hearingLoop", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenDisabledAccessIsTrue_thenBuildAnAppealWithArrangementsWithDisabledAccess() {

        pairs.put("hearing_options_accessible_hearing_rooms", DISABLED_ACCESS);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertEquals("disabledAccess", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenASignLanguageInterpreterIsTrueAndTypeIsEntered_thenBuildAnAppealWithArrangementsWithSignLanguageInterpreterAndTypeSetToOcrType() {

        pairs.put("hearing_options_sign_language_interpreter", SIGN_LANGUAGE_REQUIRED);
        pairs.put("hearing_options_sign_language_type", SIGN_LANGUAGE_TYPE);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertEquals("signLanguageInterpreter", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));
        assertEquals(SIGN_LANGUAGE_TYPE, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getSignLanguageType());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenASignLanguageInterpreterIsTrueAndTypeIsNotEntered_thenBuildAnAppealWithArrangementsWithSignLanguageInterpreterAndTypeSetToDefaultType() {

        pairs.put("hearing_options_sign_language_interpreter", SIGN_LANGUAGE_REQUIRED);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertEquals("signLanguageInterpreter", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));
        assertEquals(DEFAULT_SIGN_LANGUAGE, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getSignLanguageType());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenASignLanguageInterpreterAndLanguageInterpreterIsEntered_thenBuildAnAppealWithSignLanguageAndIgnoreLanguageInterpreter() {

        pairs.put("hearing_options_language_type", HEARING_OPTIONS_LANGUAGE_TYPE);
        pairs.put("hearing_options_sign_language_interpreter", SIGN_LANGUAGE_REQUIRED);
        pairs.put("hearing_options_sign_language_type", SIGN_LANGUAGE_TYPE);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertEquals("signLanguageInterpreter", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getArrangements().get(0));
        assertEquals(SIGN_LANGUAGE_TYPE, ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getSignLanguageType());
        assertEquals("No", ((Appeal) result.getTransformedCase().get("appeal")).getHearingOptions().getLanguageInterpreter());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenACaseWithNullOcrData_thenAddErrorToList() {

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(null).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertTrue(result.getErrors().contains("No OCR data, case cannot be created"));
    }

    @Test
    public void givenACaseWithNoOcrData_thenAddErrorToList() {
        Map<String, Object> noPairs = ImmutableMap.<String, Object>builder().build();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(noPairs).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertTrue(result.getErrors().contains("No OCR data, case cannot be created"));
    }

    @Test
    public void givenOneDocument_thenBuildACase() {
        List<ScannedRecord> records = new ArrayList<>();
        ScannedRecord scannedRecord = buildTestScannedRecord(DocumentLink.builder().documentUrl("www.test.com").build());
        records.add(scannedRecord);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).records(records).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        @SuppressWarnings("unchecked")
        List<SscsDocument> docs = ((List<SscsDocument>) result.getTransformedCase().get("sscsDocument"));
        assertEquals(scannedRecord.getDocScanDate(), docs.get(0).getValue().getDocumentDateAdded());
        assertEquals(scannedRecord.getFilename(), docs.get(0).getValue().getDocumentFileName());
        assertEquals(scannedRecord.getDocumentLink().getDocumentUrl(), docs.get(0).getValue().getDocumentLink().getDocumentUrl());
        assertEquals("Other document", docs.get(0).getValue().getDocumentType());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenMultipleDocuments_thenBuildACase() {
        List<ScannedRecord> records = new ArrayList<>();
        ScannedRecord scannedRecord1 = buildTestScannedRecord(DocumentLink.builder().documentUrl("http://www.test1.com").build());
        ScannedRecord scannedRecord2 = buildTestScannedRecord(DocumentLink.builder().documentUrl("http://www.test2.com").build());
        records.add(scannedRecord1);
        records.add(scannedRecord2);

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(ScannedData.builder().ocrCaseData(pairs).records(records).build());

        CaseValidationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        @SuppressWarnings("unchecked")
        List<SscsDocument> docs = ((List<SscsDocument>) result.getTransformedCase().get("sscsDocument"));
        assertEquals(scannedRecord1.getDocScanDate(), docs.get(0).getValue().getDocumentDateAdded());
        assertEquals(scannedRecord1.getFilename(), docs.get(0).getValue().getDocumentFileName());
        assertEquals(scannedRecord1.getDocumentLink().getDocumentUrl(), docs.get(0).getValue().getDocumentLink().getDocumentUrl());
        assertEquals("Other document", docs.get(0).getValue().getDocumentType());
        assertEquals(scannedRecord2.getDocScanDate(), docs.get(1).getValue().getDocumentDateAdded());
        assertEquals(scannedRecord2.getFilename(), docs.get(1).getValue().getDocumentFileName());
        assertEquals(scannedRecord2.getDocumentLink().getDocumentUrl(), docs.get(1).getValue().getDocumentLink().getDocumentUrl());
        assertEquals("Other document", docs.get(1).getValue().getDocumentType());

        assertTrue(result.getErrors().isEmpty());
    }

    private Appeal buildTestAppealData() {
        Name appellantName = Name.builder().title(APPELLANT_TITLE).firstName(APPELLANT_FIRST_NAME).lastName(APPELLANT_LAST_NAME).build();
        Address appellantAddress = Address.builder().line1(APPELLANT_ADDRESS_LINE1).line2(APPELLANT_ADDRESS_LINE2).town(APPELLANT_ADDRESS_LINE3).county(APPELLANT_ADDRESS_LINE4).postcode(APPELLANT_POSTCODE).build();
        Identity appellantIdentity = Identity.builder().nino(APPELLANT_NINO).dob("1987-08-12").build();
        Contact appellantContact = Contact.builder().phone(APPELLANT_PHONE).mobile(APPELLANT_MOBILE).build();
        Appellant appellant = Appellant.builder().name(appellantName).identity(appellantIdentity).address(appellantAddress).contact(appellantContact).build();

        Name repName = Name.builder().title(REPRESENTATIVE_PERSON_TITLE).firstName(REPRESENTATIVE_PERSON_FIRST_NAME).lastName(REPRESENTATIVE_PERSON_LAST_NAME).build();
        Address repAddress = Address.builder().line1(REPRESENTATIVE_ADDRESS_LINE1).line2(REPRESENTATIVE_ADDRESS_LINE2).town(REPRESENTATIVE_ADDRESS_LINE3).county(REPRESENTATIVE_ADDRESS_LINE4).postcode(REPRESENTATIVE_POSTCODE).build();
        Contact repContact = Contact.builder().phone(REPRESENTATIVE_PHONE_NUMBER).build();

        ExcludeDate excludeDate = ExcludeDate.builder().value(DateRange.builder().start(HEARING_OPTIONS_EXCLUDE_DATES).build()).build();
        List<ExcludeDate> excludedDates = new ArrayList<>();
        excludedDates.add(excludeDate);

        List<String> hearingSupportArrangements = new ArrayList<>();
        hearingSupportArrangements.add("hearingLoop");

        return Appeal.builder()
            .benefitType(BenefitType.builder().code(BENEFIT_TYPE_DESCRIPTION).build())
            .appellant(appellant)
            .rep(Representative.builder().hasRepresentative(YES_LITERAL).name(repName).address(repAddress).contact(repContact).organisation(REPRESENTATIVE_NAME).build())
            .mrnDetails(MrnDetails.builder().mrnLateReason(APPEAL_LATE_REASON).build())
            .hearingType(HEARING_TYPE_ORAL)
            .hearingOptions(HearingOptions.builder()
                .excludeDates(excludedDates)
                .arrangements(hearingSupportArrangements)
                .languageInterpreter(YES_LITERAL)
                .languages(HEARING_OPTIONS_LANGUAGE_TYPE)
                .wantsToAttend(YES_LITERAL)
                .wantsSupport(YES_LITERAL).build())
            .signer(SIGNATURE_NAME)
            .build();
    }

    private ScannedRecord buildTestScannedRecord(DocumentLink link) {
        return ScannedRecord.builder()
            .docScanDate("2018-08-10")
            .documentControlNumber("123")
            .documentLink(link)
            .filename("mrn.jpg")
            .documentType("Testing").build();

    }

}
