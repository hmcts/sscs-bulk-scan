package uk.gov.hmcts.reform.sscs.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.sscs.ccd.domain.CaseManagementLocation;
import uk.gov.hmcts.reform.sscs.model.CourtVenue;

@RunWith(MockitoJUnitRunner.class)
public class CaseManagementLocationServiceTest {

    @Mock
    private RefDataService refDataService;

    @Mock
    private RpcVenueService rpcVenueService;

    private CaseManagementLocationService caseManagementLocationService;

    @Before
    public void setup() {
        caseManagementLocationService = new CaseManagementLocationService(
            refDataService,
            rpcVenueService,
            true);
    }

    @Test
    public void shouldNotRetrieveCaseManagementLocation_givenCaseAccessManagementFeatureIsDisabled() {
        ReflectionTestUtils.setField(caseManagementLocationService, "caseAccessManagementFeature", false);

        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("venue", "postcode");

        assertTrue(caseManagementLocation.isEmpty());
    }

    @Test
    public void shouldNotRetrieveCaseManagementLocation_givenBlankProcessingVenue() {
        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("", "postcode");

        assertTrue(caseManagementLocation.isEmpty());
    }

    @Test
    public void shouldNotRetrieveCaseManagementLocation_givenBlankPostcode() {
        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("venue", "");

        assertTrue(caseManagementLocation.isEmpty());
    }

    @Test
    public void shouldNotRetrieveCaseManagementLocation_givenInvalidProcessingVenue() {
        when(refDataService.getVenueRefData("Bradford")).thenReturn(null);

        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("Bradford", "BD1 1RX");

        assertTrue(caseManagementLocation.isEmpty());
    }

    @Test
    public void shouldNotRetrieveCaseManagementLocation_givenInvalidCourtVenue() {
        when(refDataService.getVenueRefData("Bradford")).thenReturn(CourtVenue.builder().build());

        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("Bradford", "BD1 1RX");

        assertTrue(caseManagementLocation.isEmpty());
    }

    @Test
    public void shouldNotRetrieveCaseManagementLocation_givenInvalidPostcode() {
        when(refDataService.getVenueRefData("Bradford")).thenReturn(CourtVenue.builder().regionId("regionId").build());
        when(rpcVenueService.retrieveRpcEpimsIdForPostcode("BD1 1RX")).thenReturn(null);

        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("Bradford", "BD1 1RX");

        assertTrue(caseManagementLocation.isEmpty());
    }

    @Test
    public void shouldRetrieveCaseManagementLocation_givenValidProcessingVenue_andPostcode() {
        when(refDataService.getVenueRefData("Bradford")).thenReturn(CourtVenue.builder().regionId("regionId").build());
        when(rpcVenueService.retrieveRpcEpimsIdForPostcode("BD1 1RX")).thenReturn("rpcEpimsId");

        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("Bradford", "BD1 1RX");

        assertTrue(caseManagementLocation.isPresent());
        CaseManagementLocation result = caseManagementLocation.get();
        assertEquals("rpcEpimsId", result.getBaseLocation());
        assertEquals("regionId", result.getRegion());
    }
}