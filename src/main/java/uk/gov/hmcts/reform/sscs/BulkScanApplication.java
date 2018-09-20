package uk.gov.hmcts.reform.sscs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(
    basePackages = "uk.gov.hmcts.reform.sscs",
    basePackageClasses = BulkScanApplication.class,
    lazyInit = true
)
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, its not a utility class
public class BulkScanApplication {

    public static void main(final String[] args) {
        SpringApplication.run(BulkScanApplication.class, args);
    }
}
