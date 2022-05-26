package uk.gov.hmcts.reform.sscs.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.sscs.ccd.domain.Address;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;
import uk.gov.hmcts.reform.sscs.ccd.domain.RegionalProcessingCenter;
import uk.gov.hmcts.reform.sscs.helper.AppellantPostcodeHelper;
import uk.gov.hmcts.reform.sscs.service.exception.VenueLookupException;

@RunWith(MockitoJUnitRunner.class)
public class RpcVenueServiceTest {

    @Mock
    private AppellantPostcodeHelper appellantPostcodeHelper;

    @Mock
    private RegionalProcessingCenterService regionalProcessingCenterService;

    @Mock
    private VenueService venueService;

    @InjectMocks
    private RpcVenueService rpcVenueService;

    @Test
    public void shouldGetEpimsId_givenValidAppellant() {
        when(appellantPostcodeHelper.resolvePostcode(any())).thenReturn("appellantPostcode");
        when(regionalProcessingCenterService.getByPostcode("appellantPostcode")).thenReturn(RegionalProcessingCenter.builder().postcode("rpcPostcode").build());
        when(venueService.getEpimsIdForActiveVenueByPostcode("rpcPostcode")).thenReturn(Optional.of("rpcEpimsId"));

        Appellant testAppellant = Appellant.builder()
            .address(Address.builder()
                .postcode("appellantPostcode")
                .build())
            .build();

        String actualPostcode = rpcVenueService.retrieveRpcEpimsIdForAppellant(testAppellant);

        assertEquals("rpcEpimsId", actualPostcode);
    }

    @Test
    public void shouldThrowVenueLookupException_givenEpimsIdIsEmpty() {
        when(appellantPostcodeHelper.resolvePostcode(any())).thenReturn("appellantPostcode");
        when(regionalProcessingCenterService.getByPostcode("appellantPostcode")).thenReturn(RegionalProcessingCenter.builder().postcode("rpcPostcode").build());
        when(venueService.getEpimsIdForActiveVenueByPostcode("rpcPostcode")).thenReturn(Optional.empty());

        Appellant testAppellant = Appellant.builder()
            .address(Address.builder()
                .postcode("appellantPostcode")
                .build())
            .build();

        assertThrows(VenueLookupException.class, () ->
            rpcVenueService.retrieveRpcEpimsIdForAppellant(testAppellant));
    }

}