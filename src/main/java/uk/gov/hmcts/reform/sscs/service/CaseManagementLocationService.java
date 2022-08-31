package uk.gov.hmcts.reform.sscs.service;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.CaseManagementLocation;
import uk.gov.hmcts.reform.sscs.ccd.domain.RegionalProcessingCenter;
import uk.gov.hmcts.reform.sscs.model.CourtVenue;

@Service
public class CaseManagementLocationService {

    private final RefDataService refDataService;
    private final boolean caseAccessManagementFeature;

    public CaseManagementLocationService(RefDataService refDataService,
                                         @Value("${feature.case-access-management.enabled}") boolean caseAccessManagementFeature) {
        this.refDataService = refDataService;
        this.caseAccessManagementFeature = caseAccessManagementFeature;
    }

    public Optional<CaseManagementLocation> retrieveCaseManagementLocation(String processingVenue, RegionalProcessingCenter
            regionalProcessingCenter) {
        if (caseAccessManagementFeature
            && isNotBlank(processingVenue)
            && nonNull(regionalProcessingCenter)) {

            CourtVenue courtVenue = refDataService.getVenueRefData(processingVenue);

            if (nonNull(courtVenue)
                && isNotBlank(courtVenue.getRegionId())) {
                return Optional.of(CaseManagementLocation.builder()
                    .baseLocation(regionalProcessingCenter.getEpimsId())
                    .region(courtVenue.getRegionId())
                    .build());
            }
        }
        return Optional.empty();
    }

}
