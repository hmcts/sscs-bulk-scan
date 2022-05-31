package uk.gov.hmcts.reform.sscs.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.RegionalProcessingCenter;
import uk.gov.hmcts.reform.sscs.service.exception.VenueLookupException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RpcVenueService {

    private static final String VENUE_ERROR_MESSAGE_TEMPLATE = "Unable to retrieve venue epims id for RPC %s";

    private final RegionalProcessingCenterService regionalProcessingCenterService;
    private final VenueService venueService;

    public String retrieveRpcEpimsIdForPostcode(@NonNull String postcode) {
        RegionalProcessingCenter rpc = regionalProcessingCenterService.getByPostcode(postcode);
        return venueService.getEpimsIdForActiveVenueByPostcode(rpc.getPostcode())
            .orElseThrow(() -> {
                String venueErrorMessage = String.format(VENUE_ERROR_MESSAGE_TEMPLATE, rpc);
                log.error(venueErrorMessage);
                throw new VenueLookupException(venueErrorMessage);
            });
    }
}
