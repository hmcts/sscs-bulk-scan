package uk.gov.hmcts.reform.sscs.controllers;

import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.sscs.service.AuthorisationService;

@RestController
public class BulkScanController {

    private static final org.slf4j.Logger LOG = getLogger(BulkScanController.class);

    @Autowired
    public BulkScanController() {

    }

    @RequestMapping(value = "/send", method = POST, produces = APPLICATION_JSON_VALUE)
    public void sendNotification(
            @RequestHeader(AuthorisationService.SERVICE_AUTHORISATION_HEADER) String serviceAuthHeader,
            @RequestBody JsonNode node) {
        LOG.info("Bulk scan response: " + node);

    }

    @RequestMapping(value = "/send1", method = POST, produces = APPLICATION_JSON_VALUE)
    public void sendNotification1(
        @RequestHeader(AuthorisationService.SERVICE_AUTHORISATION_HEADER) String serviceAuthHeader,
        @RequestBody JsonNode node) {
        LOG.info("Bulk scan response1: " + node);

    }
}
