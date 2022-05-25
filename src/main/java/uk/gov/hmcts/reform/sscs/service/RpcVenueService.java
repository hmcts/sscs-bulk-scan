package uk.gov.hmcts.reform.sscs.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;
import uk.gov.hmcts.reform.sscs.ccd.domain.RegionalProcessingCenter;
import uk.gov.hmcts.reform.sscs.helper.AppellantPostcodeHelper;

@Service
@RequiredArgsConstructor
public class RpcVenueService {

    private final AppellantPostcodeHelper appellantPostcodeHelper;
    private final RegionalProcessingCenterService regionalProcessingCenterService;
    private final VenueService venueService;

    public String retrieveRpcEpimsIdForAppellant(Appellant appellant) {
        String appellantPostcode = appellantPostcodeHelper.resolvePostcode(appellant);
        RegionalProcessingCenter rpc = regionalProcessingCenterService.getByPostcode(appellantPostcode);
        return venueService.getEpimsIdForActiveVenueByPostcode(rpc.getPostcode()).orElse(null);
    }
}
