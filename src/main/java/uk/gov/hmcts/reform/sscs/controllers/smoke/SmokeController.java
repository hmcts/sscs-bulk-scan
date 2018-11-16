package uk.gov.hmcts.reform.sscs.controllers.smoke;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.ccd.service.SearchCcdCaseService;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@Controller
public class SmokeController {

    @Autowired
    private SearchCcdCaseService searchCcdCaseService;

//    @GetMapping("/smoke-test")
//    @ResponseBody
//    public List<SscsCaseDetails> smoke() {
//
//        return searchCcdCaseService.findCaseByCaseRef("SC068/18/01217", IdamTokens.builder().build().idamService.getIdamTokens());
//    }

}
