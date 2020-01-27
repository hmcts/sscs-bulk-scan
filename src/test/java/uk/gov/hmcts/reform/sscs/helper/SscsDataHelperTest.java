package uk.gov.hmcts.reform.sscs.helper;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.MrnDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocument;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;

@RunWith(SpringRunner.class)
public class SscsDataHelperTest {

    private SscsDataHelper caseDataHelper;

    @Mock
    private DwpAddressLookupService dwpAddressLookupService;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Before
    public void setUp() {
        caseDataHelper = new SscsDataHelper(new CaseEvent("appealCreated", "validAppealCreated", "incompleteApplicationReceived", "nonCompliant"), new ArrayList<>(), dwpAddressLookupService);
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
}
