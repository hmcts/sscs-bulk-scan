package uk.gov.hmcts.reform.sscs.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.ccd.service.SscsCcdConvertService;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.service.EvidenceManagementService;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;
import uk.gov.hmcts.reform.sscs.service.RoboticsService;

public class SscsRoboticsHandlerTest {

    SscsRoboticsHandler sscsRoboticsHandler;

    @Mock
    EvidenceManagementService evidenceManagementService;

    @Mock
    RoboticsService roboticsService;

    @Mock
    RegionalProcessingCenterService regionalProcessingCenterService;

    SscsCcdConvertService convertService;

    LocalDate localDate;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Before
    public void setup() {
        initMocks(this);

        convertService = new SscsCcdConvertService();

        sscsRoboticsHandler = new SscsRoboticsHandler(roboticsService, regionalProcessingCenterService,
            convertService, evidenceManagementService, new CaseEvent("appealCreated", "incompleteApplicationReceived", "nonCompliant"), true);

        localDate = LocalDate.now();
    }

    @Test
    public void givenACaseWithCaseCreatedEvent_thenCreateRoboticsFile() {

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build())
            .appellant(Appellant.builder().address(
                Address.builder().postcode("CM120HN").build())
            .build()).build();

        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        given(evidenceManagementService.download(any(), eq(null))).willReturn(null);

        given(regionalProcessingCenterService.getFirstHalfOfPostcode("CM120HN")).willReturn("CM12");

        SscsCaseData sscsCaseData = SscsCaseData.builder().appeal(appeal).build();

        when(roboticsService
            .sendCaseToRobotics(sscsCaseData, 1L, "CM12", null, Collections.emptyMap()))
            .thenReturn(null);

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        sscsRoboticsHandler.handle(caseValidationResponse, 1L, "appealCreated");

        verify(roboticsService).sendCaseToRobotics(sscsCaseData, 1L, "CM12", null, Collections.emptyMap());
    }

    @Test
    public void givenACaseWithCaseCreatedEventAndFeatureFlagDisabled_thenDoNotCreateRoboticsFile() {
        ReflectionTestUtils.setField(sscsRoboticsHandler, "roboticsEnabled", false);

        Appeal appeal = Appeal.builder().build();

        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        sscsRoboticsHandler.handle(caseValidationResponse, 1L, "appealCreated");

        verifyNoMoreInteractions(roboticsService);
    }

    @Test
    public void givenACaseWithCaseCreatedEventAndEvidenceToDownload_thenCreateRoboticsFileWithDownloadedEvidence() {

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build())
            .appellant(Appellant.builder().address(
                Address.builder().postcode("CM120HN").build())
                .build()).build();

        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        List<SscsDocument> documents = new ArrayList<>();
        documents.add(SscsDocument.builder()
            .value(SscsDocumentDetails.builder()
                .documentFileName("test.jpg")
                .documentLink(DocumentLink.builder().documentUrl("www.download.com").build())
                .build())
            .build());
        transformedCase.put("sscsDocument", documents);

        given(regionalProcessingCenterService.getFirstHalfOfPostcode("CM120HN")).willReturn("CM12");

        byte[] expectedBytes = {1, 2, 3};
        given(evidenceManagementService.download(URI.create("www.download.com"), null)).willReturn(expectedBytes);

        SscsCaseData sscsCaseData = SscsCaseData.builder().appeal(appeal).sscsDocument(documents).build();

        Map<String, byte[]> expectedAdditionalEvidence = new HashMap<>();
        expectedAdditionalEvidence.put("test.jpg", expectedBytes);
        when(roboticsService
            .sendCaseToRobotics(sscsCaseData, 1L, "CM12", null, expectedAdditionalEvidence))
            .thenReturn(null);

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        sscsRoboticsHandler.handle(caseValidationResponse, 1L, "appealCreated");

        verify(roboticsService).sendCaseToRobotics(sscsCaseData, 1L, "CM12", null, expectedAdditionalEvidence);
    }

    @Test
    public void givenACaseWithNoCaseCreatedEvent_thenDoNotInteractWithRoboticsService() {
        CaseResponse caseValidationResponse = CaseResponse.builder().build();

        sscsRoboticsHandler.handle(caseValidationResponse, 1L, "incompleteApplicationReceived");

        verifyNoMoreInteractions(roboticsService);
    }

}
