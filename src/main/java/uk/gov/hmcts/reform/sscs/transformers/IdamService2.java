package uk.gov.hmcts.reform.sscs.transformers;

import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.proc.JWSVerifierFactory;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.sscs.idam.Authorize;
import uk.gov.hmcts.reform.sscs.idam.IdamApiClient;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.idam.UserDetails;

@Service
@Slf4j
public class IdamService2 {
    public static final int ONE_HOUR = 1000 * 60 * 60;

    private final AuthTokenGenerator authTokenGenerator;
    private final IdamApiClient idamApiClient;
    private final JWSVerifierFactory jwsVerifierFactory;

    @Value("${idam.oauth2.user.email}")
    private String idamOauth2UserEmail;

    @Value("${idam.oauth2.user.password}")
    private String idamOauth2UserPassword;

    @Value("${idam.oauth2.client.id}")
    private String idamOauth2ClientId;

    @Value("${idam.oauth2.client.secret}")
    private String idamOauth2ClientSecret;

    @Value("${idam.oauth2.redirectUrl}")
    private String idamOauth2RedirectUrl;

    @Value("${idam.url}")
    private String idamUrl;

    // Tactical idam token caching solution implemented
    // SSCS-5895 - will deliver the strategic caching solution
    private String cachedToken;

    @Autowired
    IdamService2(AuthTokenGenerator authTokenGenerator, IdamApiClient idamApiClient) {
        this.authTokenGenerator = authTokenGenerator;
        this.idamApiClient = idamApiClient;
        this.jwsVerifierFactory = new DefaultJWSVerifierFactory();
    }

    public String generateServiceAuthorization() {
        return authTokenGenerator.generate();
    }

    @Retryable
    public String getUserId(String oauth2Token) {
        return idamApiClient.getUserDetails(oauth2Token).getId();
    }

    public UserDetails getUserDetails(String oauth2Token)  {
        return idamApiClient.getUserDetails(oauth2Token);
    }

    public String getIdamOauth2Token() {

        try {
            log.info("Requesting idam token");
            log.info(("idamOauth2UserEmail: " + idamOauth2UserEmail));
            log.info(("idamOauth2UserPassword: " + idamOauth2UserPassword));
            String authorisation = idamOauth2UserEmail + ":" + idamOauth2UserPassword;
            log.info(("authorisation: " + authorisation));
            String base64Authorisation = Base64.getEncoder().encodeToString(authorisation.getBytes());
            log.info(("base64Authorisation: " + base64Authorisation));
            log.info(("idamOauth2ClientId: " + idamOauth2ClientId));
            log.info(("idamOauth2ClientSecret: " + idamOauth2ClientSecret));
            log.info(("idamOauth2RedirectUrl: " + idamOauth2RedirectUrl));
            log.info(("idamUrl: " + idamUrl));

            Authorize authorize = idamApiClient.authorizeCodeType(
                "Basic " + base64Authorisation,
                "code",
                idamOauth2ClientId,
                idamOauth2RedirectUrl,
                " "
            );

            log.info("Passing authorization code to IDAM to get a token");

            Authorize authorizeToken = idamApiClient.authorizeToken(
                authorize.getCode(),
                "authorization_code",
                idamOauth2RedirectUrl,
                idamOauth2ClientId,
                idamOauth2ClientSecret,
                " "
            );

            cachedToken = "Bearer " + authorizeToken.getAccessToken();

            log.info("Requesting idam token successful");

            return cachedToken;
        } catch (Exception e) {
            log.error("Requesting idam token failed: " + e.getMessage());
            throw e;
        }
    }

    @Scheduled(fixedRate = ONE_HOUR)
    public void evictCacheAtIntervals() {
        log.info("Evicting idam token cache");
        cachedToken = null;
    }

    public IdamTokens getIdamTokens() {

        String idamOauth2Token;

        if (StringUtils.isEmpty(cachedToken)) {
            log.info("No cached IDAM token found, requesting from IDAM service.");
            idamOauth2Token =  getIdamOauth2Token();
        } else {
            log.info("Using cached IDAM token.");
            idamOauth2Token =  cachedToken;
        }

        UserDetails userDetails = getUserDetails(idamOauth2Token);
        return IdamTokens.builder()
                .idamOauth2Token(idamOauth2Token)
                .serviceAuthorization(generateServiceAuthorization())
                .userId(userDetails.getId())
                .email(userDetails.getEmail())
                .roles(userDetails.getRoles())
                .build();
    }
}
