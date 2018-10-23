package uk.gov.hmcts.reform.sscs.transformers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static org.mockito.BDDMockito.given;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.*;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseTransformationResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;


public class SscsCaseTransformerTest {

    @Mock
    SscsJsonExtractor sscsJsonExtractor;

    @InjectMocks
    SscsCaseTransformer transformer;

    Map<String, Object> ocrMap = new HashMap<>();

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void givenKeyValuePairs_thenBuildAnAppeal() {

        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("benefit_type_description", BENEFIT_TYPE_DESCRIPTION)
            .put("appellant_title", APPELLANT_TITLE)
            .put("appellant_first_name", APPELLANT_FIRST_NAME)
            .put("appellant_last_name", APPELLANT_LAST_NAME)
            .put("appellant_address_line1", APPELLANT_ADDRESS_LINE1)
            .put("appellant_address_line2", APPELLANT_ADDRESS_LINE2)
            .put("appellant_address_line3", APPELLANT_ADDRESS_LINE3)
            .put("appellant_address_line4", APPELLANT_ADDRESS_LINE4)
            .put("appellant_postcode", APPELLANT_POSTCODE)
            .put("appellant_phone", APPELLANT_PHONE)
            .put("appellant_mobile", APPELLANT_MOBILE)
            .put("appellant_date_of_birth", APPELLANT_DATE_OF_BIRTH)
            .put("appellant_ni_number", APPELLANT_NI_NUMBER)
            .put("is_appointee", IS_APPOINTEE)
            .put("is_not_appointee", IS_NOT_APPOINTEE)
            .put("appointee_title", APPOINTEE_TITLE)
            .put("appointee_first_name", APPOINTEE_FIRST_NAME)
            .put("appointee_last_name", APPOINTEE_LAST_NAME)
            .put("appointee_address_line1", APPOINTEE_ADDRESS_LINE1)
            .put("appointee_address_line2", APPOINTEE_ADDRESS_LINE2)
            .put("appointee_address_line3", APPOINTEE_ADDRESS_LINE3)
            .put("appointee_address_line4", APPOINTEE_ADDRESS_LINE4)
            .put("appointee_postcode", APPOINTEE_POSTCODE)
            .put("appointee_date_of_birth", APPOINTEE_DATE_OF_BIRTH)
            .put("appointee_ni_number", APPOINTEE_NI_NUMBER)
            .put("representative_name", REPRESENTATIVE_NAME)
            .put("representative_address_line1", REPRESENTATIVE_ADDRESS_LINE1)
            .put("representative_address_line2", REPRESENTATIVE_ADDRESS_LINE2)
            .put("representative_address_line3", REPRESENTATIVE_ADDRESS_LINE3)
            .put("representative_address_line4", REPRESENTATIVE_ADDRESS_LINE4)
            .put("representative_postcode", REPRESENTATIVE_POSTCODE)
            .put("representative_phone_number", REPRESENTATIVE_PHONE_NUMBER)
            .put("representative_person_title", REPRESENTATIVE_PERSON_TITLE)
            .put("representative_person_first_name", REPRESENTATIVE_PERSON_FIRST_NAME)
            .put("representative_person_last_name", REPRESENTATIVE_PERSON_LAST_NAME)
            .put("appeal_late_reason", APPEAL_LATE_REASON)
            .put("is_hearing_type_oral", IS_HEARING_TYPE_ORAL)
            .put("is_hearing_type_paper", IS_HEARING_TYPE_PAPER)
            .put("hearing_options_exclude_dates", HEARING_OPTIONS_EXCLUDE_DATES)
            .put("hearing_support_arrangements", HEARING_SUPPORT_ARRANGEMENTS)
            .put("hearing_options_language", HEARING_OPTIONS_LANGUAGE)
            .put("agree_less_hearing_notice_yes", AGREE_LESS_HEARING_NOTICE_YES)
            .put("agree_less_hearing_notice_no", AGREE_LESS_HEARING_NOTICE_NO)
            .put("signature_appellant_name", SIGNATURE_APPELLANT_NAME)
            .build();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(pairs);

        CaseTransformationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertEquals(buildTestAppealData(), result.getTransformedCase().get("appeal"));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenOnlyOneKeyValuePair_thenBuildAnAppeal() {

        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("benefit_type_description", BENEFIT_TYPE_DESCRIPTION).build();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(pairs);

        CaseTransformationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertEquals(BENEFIT_TYPE_DESCRIPTION, ((Appeal) result.getTransformedCase().get("appeal")).getBenefitType().getCode());

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void givenContradictingPaperAndOralCaseValues_thenAddErrorToList() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("is_hearing_type_oral", true)
            .put("is_hearing_type_paper", true).build();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(pairs);

        CaseTransformationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertTrue(result.getErrors().contains("is_hearing_type_oral and is_hearing_type_paper have contradicting values"));
    }

    @Test
    public void givenContradictingIsAppointeeValues_thenAddErrorToList() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("is_appointee", true)
            .put("is_not_appointee", true).build();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(pairs);

        CaseTransformationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertTrue(result.getErrors().contains("is_appointee and is_not_appointee have contradicting values"));
    }

    @Test
    public void givenBooleanValueIsText_thenAddErrorToList() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("is_appointee", "I am a text value")
            .put("is_not_appointee", true).build();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(pairs);

        CaseTransformationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertTrue(result.getErrors().contains("is_appointee does not contain a valid boolean value. Needs to be true or false"));
    }

    @Test
    public void givenIsAppointeeBooleanValueIsEmpty_thenIgnoreField() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("is_not_appointee", true).build();

        given(sscsJsonExtractor.extractJson(ocrMap)).willReturn(pairs);

        CaseTransformationResponse result = transformer.transformExceptionRecordToCase(ocrMap);

        assertNull(((Appeal) result.getTransformedCase().get("appeal")).getAppellant().getIsAppointee());

        assertTrue(result.getErrors().isEmpty());
    }

    private Appeal buildTestAppealData() {
        Name appellantName = Name.builder().title(APPELLANT_TITLE).firstName(APPELLANT_FIRST_NAME).lastName(APPELLANT_LAST_NAME).build();
        Address appellantAddress = Address.builder().line1(APPELLANT_ADDRESS_LINE1).line2(APPELLANT_ADDRESS_LINE2).town(APPELLANT_ADDRESS_LINE3).county(APPELLANT_ADDRESS_LINE4).postcode(APPELLANT_POSTCODE).build();
        Identity appellantIdentity = Identity.builder().nino(APPELLANT_NI_NUMBER).dob(APPELLANT_DATE_OF_BIRTH).build();
        Contact appellantContact = Contact.builder().phone(APPELLANT_PHONE).mobile(APPELLANT_MOBILE).build();

        Name repName = Name.builder().title(REPRESENTATIVE_PERSON_TITLE).firstName(REPRESENTATIVE_PERSON_FIRST_NAME).lastName(REPRESENTATIVE_PERSON_LAST_NAME).build();
        Address repAddress = Address.builder().line1(REPRESENTATIVE_ADDRESS_LINE1).line2(REPRESENTATIVE_ADDRESS_LINE2).town(REPRESENTATIVE_ADDRESS_LINE3).county(REPRESENTATIVE_ADDRESS_LINE4).postcode(REPRESENTATIVE_POSTCODE).build();
        Contact repContact = Contact.builder().phone(REPRESENTATIVE_PHONE_NUMBER).build();

        ExcludeDate excludeDate = ExcludeDate.builder().value(DateRange.builder().start(HEARING_OPTIONS_EXCLUDE_DATES).build()).build();
        List<ExcludeDate> excludedDates = new ArrayList<>();
        excludedDates.add(excludeDate);

        List<String> hearingSupportArrangements = new ArrayList<>();
        hearingSupportArrangements.add(HEARING_SUPPORT_ARRANGEMENTS);

        return Appeal.builder()
            .benefitType(BenefitType.builder().code(BENEFIT_TYPE_DESCRIPTION).build())
            .appellant(Appellant.builder().name(appellantName).identity(appellantIdentity).address(appellantAddress).contact(appellantContact).isAppointee("Yes").build())
            .rep(Representative.builder().hasRepresentative("Yes").name(repName).address(repAddress).contact(repContact).organisation(REPRESENTATIVE_NAME).build())
            .mrnDetails(MrnDetails.builder().mrnLateReason(APPEAL_LATE_REASON).build())
            .hearingType("Oral")
            .hearingOptions(HearingOptions.builder().excludeDates(excludedDates).arrangements(hearingSupportArrangements).languageInterpreter("Yes").languages(HEARING_OPTIONS_LANGUAGE).build())
            .signer(SIGNATURE_APPELLANT_NAME)
        .build();
    }

}
