package uk.gov.hmcts.reform.sscs.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.sscs.ccd.domain.CaseManagementLocation;
import uk.gov.hmcts.reform.sscs.ccd.domain.RegionalProcessingCenter;
import uk.gov.hmcts.reform.sscs.model.CourtVenue;

@RunWith(MockitoJUnitRunner.class)
public class CaseManagementLocationServiceTest {

    @Mock
    private RefDataService refDataService;

    private CaseManagementLocationService caseManagementLocationService;

    public void setupCaseManagementLocationService(boolean feature) {
        caseManagementLocationService = new CaseManagementLocationService(
            refDataService, feature);
    }

    @Test
    public void shouldNotRetrieveCaseManagementLocation_givenCaseAccessManagementFeatureIsDisabled() {
        setupCaseManagementLocationService(false);
        RegionalProcessingCenter regionalProcessingCentre = RegionalProcessingCenter.builder().build();

        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("venue",
                regionalProcessingCentre);

        assertTrue(caseManagementLocation.isEmpty());
    }

    @Test
    public void shouldNotRetrieveCaseManagementLocation_givenBlankProcessingVenue() {
        setupCaseManagementLocationService(true);

        RegionalProcessingCenter regionalProcessingCentre = RegionalProcessingCenter.builder().build();

        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("", regionalProcessingCentre);

        assertTrue(caseManagementLocation.isEmpty());
    }

    @Test
    public void shouldNotRetrieveCaseManagementLocation_givenBlankPostcode() {
        setupCaseManagementLocationService(true);

        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("venue", null);

        assertTrue(caseManagementLocation.isEmpty());
    }

    @Test
    public void shouldNotRetrieveCaseManagementLocation_givenInvalidProcessingVenue() {
        setupCaseManagementLocationService(true);

        RegionalProcessingCenter regionalProcessingCentre = RegionalProcessingCenter.builder().build();

        when(refDataService.getVenueRefData("Bradford")).thenReturn(null);

        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("Bradford",
                regionalProcessingCentre);

        assertTrue(caseManagementLocation.isEmpty());
    }

    @Test
    public void shouldNotRetrieveCaseManagementLocation_givenInvalidCourtVenue() {
        setupCaseManagementLocationService(true);

        RegionalProcessingCenter regionalProcessingCentre = RegionalProcessingCenter.builder().build();

        when(refDataService.getVenueRefData("Bradford")).thenReturn(CourtVenue.builder().build());

        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("Bradford",
                regionalProcessingCentre);

        assertTrue(caseManagementLocation.isEmpty());
    }

    @Test
    public void shouldRetrieveCaseManagementLocation_givenValidProcessingVenue_andPostcode() {
        setupCaseManagementLocationService(true);

        RegionalProcessingCenter regionalProcessingCentre = RegionalProcessingCenter.builder().epimsId("rpcEpimsId").build();
        when(refDataService.getVenueRefData("Bradford")).thenReturn(CourtVenue.builder().regionId("regionId").build());

        Optional<CaseManagementLocation> caseManagementLocation =
            caseManagementLocationService.retrieveCaseManagementLocation("Bradford", regionalProcessingCentre);

        assertTrue(caseManagementLocation.isPresent());
        CaseManagementLocation result = caseManagementLocation.get();
        assertEquals("rpcEpimsId", result.getBaseLocation());
        assertEquals("regionId", result.getRegion());
    }
}
