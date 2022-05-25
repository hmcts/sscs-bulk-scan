package uk.gov.hmcts.reform.sscs.helper;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.ccd.domain.Address;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appointee;
import uk.gov.hmcts.reform.sscs.validators.PostcodeValidator;

@Component
@RequiredArgsConstructor
public class AppellantPostcodeHelper {

    private final PostcodeValidator postcodeValidator;

    public String resolvePostcode(Appellant appellant) {
        return Optional.ofNullable(appellant.getAppointee())
            .map(Appointee::getAddress)
            .map(Address::getPostcode)
            .filter(this::isValidPostcode)
            .orElse(appellant.getAddress().getPostcode());
    }

    private boolean isValidPostcode(String postcode) {
        return postcodeValidator.isValidPostcodeFormat(postcode)
            && postcodeValidator.isValid(postcode);
    }

}
