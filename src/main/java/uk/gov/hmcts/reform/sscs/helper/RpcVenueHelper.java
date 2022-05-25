package uk.gov.hmcts.reform.sscs.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;
import uk.gov.hmcts.reform.sscs.ccd.domain.RegionalProcessingCenter;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;
import uk.gov.hmcts.reform.sscs.service.VenueService;

@Component
@RequiredArgsConstructor
public class RpcVenueHelper {

    private final AppellantPostcodeHelper appellantPostcodeHelper;
    private final RegionalProcessingCenterService regionalProcessingCenterService;
    private final VenueService venueService;

    public String retrieveRpcEpimsIdForAppellant(Appellant appellant) {
        String appellantPostcode = appellantPostcodeHelper.resolvePostcode(appellant);
        RegionalProcessingCenter rpc = regionalProcessingCenterService.getByPostcode(appellantPostcode);
        return venueService.getEpimsIdForActiveVenueByPostcode(rpc.getPostcode()).orElse(null);
    }
}
