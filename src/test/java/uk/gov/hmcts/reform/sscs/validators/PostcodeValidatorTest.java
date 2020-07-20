package uk.gov.hmcts.reform.sscs.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@RunWith(JUnitParamsRunner.class)
public class PostcodeValidatorTest {
    private static final String URL = "https://api.postcodes.io/postcodes/{postcode}";
    @Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private RestTemplate restTemplate;
    @Mock private ResponseEntity<byte[]> responseEntity;

    private PostcodeValidator postcodeValidator;


    @Before
    public void setup() {
        postcodeValidator = new PostcodeValidator(URL, true, restTemplate);
    }

    private void setupRestTemplateResponse() {
        when(restTemplate
            .exchange(
                any(String.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class),
                any(String.class)
            )
        ).thenReturn(responseEntity);
    }

    private void setUpSuccessResponse() {
        setupRestTemplateResponse();
        when(responseEntity.getStatusCodeValue()).thenReturn(200);
        when(responseEntity.getBody()).thenReturn("postcode".getBytes());
    }

    private void setUpFailureResponse() {
        setupRestTemplateResponse();
        when(responseEntity.getStatusCodeValue()).thenReturn(200);
        when(responseEntity.getBody()).thenReturn("unknown".getBytes());
    }

    @Test
    public void shouldReturnTrueForAValidPostCode() {
        setUpSuccessResponse();
        boolean valid = postcodeValidator.isValid("w11 1AA");
        assertTrue(valid);
    }

    @Test
    @Parameters({"W1 1aa", "70002"})
    public void shouldReturnFalseForAnInValidPostCode(String postcode) {
        setUpFailureResponse();
        assertFalse(postcodeValidator.isValid(postcode));
    }

    @Test
    public void shouldReturnTrueWhenNotEnabled() {
        PostcodeValidator postcodeValidator = new PostcodeValidator(URL, false, restTemplate);
        assertTrue(postcodeValidator.isValid("W11 1AA"));
    }
}
