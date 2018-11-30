package uk.gov.hmcts.reform.sscs.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import uk.gov.hmcts.reform.sscs.bulkscancore.ccd.CaseDataHelper;
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
    CaseDataHelper caseDataHelper;

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
            convertService, evidenceManagementService, new CaseEvent("appealCreated", "incompleteApplicationReceived", "nonCompliant"));

        localDate = LocalDate.now();
    }

    @Test
    public void givenACaseResponseWithCaseCreatedEvent_thenCreateRoboticsFile() {

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build())
            .appellant(Appellant.builder().address(
                Address.builder().postcode("CM120HN").build())
            .build()).build();

        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        byte[] expectedBytes = {1, 2, 3};
        given(evidenceManagementService.download(any(), eq(null))).willReturn(expectedBytes);

        given(regionalProcessingCenterService.getFirstHalfOfPostcode("CM120HN")).willReturn("CM12");

        SscsCaseData sscsCaseData = SscsCaseData.builder().appeal(appeal).build();

        doNothing().when(roboticsService).sendCaseToRobotics(eq(sscsCaseData), eq(1L), eq("CM12"), eq(null), any());

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        sscsRoboticsHandler.handle(caseValidationResponse, 1L, "appealCreated");

        verify(roboticsService).sendCaseToRobotics(eq(sscsCaseData), eq(1L), eq("CM12"), eq(null), any());
    }

    @Test
    public void givenACaseWithNoCaseCreatedEvent_thenDoNotInteractWithRoboticsService() {
        CaseResponse caseValidationResponse = CaseResponse.builder().build();

        sscsRoboticsHandler.handle(caseValidationResponse, 1L, "incompleteApplicationReceived");

        verifyNoMoreInteractions(roboticsService);
    }

}
