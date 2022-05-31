package uk.gov.hmcts.reform.sscs.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.sscs.ccd.domain.RegionalProcessingCenter;
import uk.gov.hmcts.reform.sscs.service.exception.VenueLookupException;

@RunWith(MockitoJUnitRunner.class)
public class RpcVenueServiceTest {

    @Mock
    private RegionalProcessingCenterService regionalProcessingCenterService;

    @Mock
    private VenueService venueService;

    @InjectMocks
    private RpcVenueService rpcVenueService;

    @Test
    public void shouldGetEpimsId_givenValidAppellant() {
        when(regionalProcessingCenterService.getByPostcode("postcode")).thenReturn(RegionalProcessingCenter.builder().postcode("rpcPostcode").build());
        when(venueService.getEpimsIdForActiveVenueByPostcode("rpcPostcode")).thenReturn(Optional.of("rpcEpimsId"));

        String actualPostcode = rpcVenueService.retrieveRpcEpimsIdForPostcode("postcode");

        assertEquals("rpcEpimsId", actualPostcode);
    }

    @Test
    public void shouldThrowVenueLookupException_givenEpimsIdIsEmpty() {
        when(regionalProcessingCenterService.getByPostcode("postcode")).thenReturn(RegionalProcessingCenter.builder().postcode("rpcPostcode").build());
        when(venueService.getEpimsIdForActiveVenueByPostcode("rpcPostcode")).thenReturn(Optional.empty());

        assertThrows(VenueLookupException.class, () ->
            rpcVenueService.retrieveRpcEpimsIdForPostcode("postcode"));
    }

}