package uk.gov.hmcts.reform.sscs.bulkscancore.ccd;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;

@RunWith(SpringRunner.class)
public class CaseDataHelperTest {

    @Mock
    private CoreCaseDataApi coreCaseDataApi;

    private CaseDataHelper caseDataHelper;

    @Before
    public void setUp() {
        caseDataHelper = new CaseDataHelper(coreCaseDataApi);
    }
}
