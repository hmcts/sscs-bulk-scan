package uk.gov.hmcts.reform.sscs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;
import uk.gov.hmcts.reform.sscs.ccd.domain.RegionalProcessingCenter;
import uk.gov.hmcts.reform.sscs.helper.AppellantPostcodeHelper;
import uk.gov.hmcts.reform.sscs.service.exception.VenueLookupException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RpcVenueService {

    private static final String VENUE_ERROR_MESSAGE_TEMPLATE = "Unable to retrieve venue epims id for RPC %s";

    private final AppellantPostcodeHelper appellantPostcodeHelper;
    private final RegionalProcessingCenterService regionalProcessingCenterService;
    private final VenueService venueService;

    public String retrieveRpcEpimsIdForAppellant(Appellant appellant) {
        String appellantPostcode = appellantPostcodeHelper.resolvePostcode(appellant);
        RegionalProcessingCenter rpc = regionalProcessingCenterService.getByPostcode(appellantPostcode);

        return venueService.getEpimsIdForActiveVenueByPostcode(rpc.getPostcode())
            .orElseThrow(() -> {
                String venueErrorMessage = String.format(VENUE_ERROR_MESSAGE_TEMPLATE, rpc);
                log.error(venueErrorMessage);
                throw new VenueLookupException(venueErrorMessage);
            });
    }
}
