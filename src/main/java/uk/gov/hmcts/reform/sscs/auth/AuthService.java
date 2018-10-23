package uk.gov.hmcts.reform.sscs.auth;

import com.google.common.base.Splitter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;
import uk.gov.hmcts.reform.sscs.exceptions.ForbiddenException;
import uk.gov.hmcts.reform.sscs.exceptions.UnAuthorizedException;

@Component
public class AuthService {

    private final AuthTokenValidator authTokenValidator;

    private final List<String> allowedServices;

    public AuthService(
        AuthTokenValidator authTokenValidator,
        @Value("${allowed-services-for-callback}") String configuredServicesCsv
    ) {
        this.authTokenValidator = authTokenValidator;
        this.allowedServices = Splitter.on(",").splitToList(configuredServicesCsv);
    }

    public String authenticate(String authHeader) {
        if (authHeader == null) {
            throw new UnAuthorizedException("Missing ServiceAuthorization header");
        } else {
            return authTokenValidator.getServiceName(authHeader);
        }
    }

    public void assertIsAllowedToHandleCallback(String serviceName) {
        if (!allowedServices.contains(serviceName)) {
            throw new ForbiddenException(
                "Service " + serviceName + " does not have permissions to request case creation"
            );
        }
    }
}
