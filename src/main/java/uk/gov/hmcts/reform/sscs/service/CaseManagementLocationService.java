package uk.gov.hmcts.reform.sscs.service;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.CaseManagementLocation;
import uk.gov.hmcts.reform.sscs.model.CourtVenue;

@Service
public class CaseManagementLocationService {

    private final RefDataService refDataService;
    private final RpcVenueService rpcVenueService;
    private final boolean caseAccessManagementFeature;

    public CaseManagementLocationService(RefDataService refDataService,
                                         RpcVenueService rpcVenueService,
                                         @Value("${feature.case-access-management.enabled}") boolean caseAccessManagementFeature) {
        this.refDataService = refDataService;
        this.rpcVenueService = rpcVenueService;
        this.caseAccessManagementFeature = caseAccessManagementFeature;
    }

    public Optional<CaseManagementLocation> retrieveCaseManagementLocation(String processingVenue, String postcode) {
        if (caseAccessManagementFeature
            && isNotBlank(processingVenue)
            && isNotBlank(postcode)) {

            CourtVenue courtVenue = refDataService.getVenueRefData(processingVenue);
            String rpcEpimsId = rpcVenueService.retrieveRpcEpimsIdForPostcode(postcode);

            if (nonNull(courtVenue)
                && isNotBlank(courtVenue.getRegionId())
                && isNotBlank(rpcEpimsId)) {
                return Optional.of(CaseManagementLocation.builder()
                    .baseLocation(rpcEpimsId)
                    .region(courtVenue.getRegionId())
                    .build());
            }
        }

        return Optional.empty();
    }

}
