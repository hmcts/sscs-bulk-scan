package uk.gov.hmcts.reform.sscs.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.sscs.ccd.domain.Address;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appointee;
import uk.gov.hmcts.reform.sscs.validators.PostcodeValidator;

@RunWith(MockitoJUnitRunner.class)
public class AppellantPostcodeHelperTest {

    @Mock
    private PostcodeValidator postcodeValidator;

    @InjectMocks
    private AppellantPostcodeHelper appellantPostcodeHelper;

    @Test
    public void shouldReturnAppointeePostcode_givenAppointeeAddressExists_andAppointeePostcodeIsValid() {
        when(postcodeValidator.isValid("CR2 8YY")).thenReturn(true);
        when(postcodeValidator.isValidPostcodeFormat("CR2 8YY")).thenReturn(true);

        Appellant testAppellant = Appellant.builder()
            .address(Address.builder()
                .postcode("TS3 6NM").build())
            .appointee(Appointee.builder()
                .address(Address.builder()
                    .postcode("CR2 8YY")
                    .build())
                .build())
            .build();

        String actualPostcode = appellantPostcodeHelper.resolvePostcode(testAppellant);

        assertEquals("CR2 8YY", actualPostcode);
    }

    @Test
    public void shouldReturnAppellantPostcode_givenAppointeeAddressExists_butAppointeePostcodeIsInvalid() {
        Appellant testAppellant = Appellant.builder()
            .address(Address.builder()
                .postcode("TS3 6NM").build())
            .appointee(Appointee.builder()
                .address(Address.builder()
                    .postcode("CR2 8YY")
                    .build())
                .build())
            .build();

        String actualPostcode = appellantPostcodeHelper.resolvePostcode(testAppellant);

        assertEquals("TS3 6NM", actualPostcode);
    }

    @Test
    public void shouldReturnAppellantPostcode_givenAppointeeAddressDoesNotExist() {
        Appellant testAppellant = Appellant.builder()
            .address(Address.builder()
                .postcode("TS3 6NM").build())
            .appointee(Appointee.builder().build())
            .build();

        String actualPostcode = appellantPostcodeHelper.resolvePostcode(testAppellant);

        assertEquals("TS3 6NM", actualPostcode);
    }

}