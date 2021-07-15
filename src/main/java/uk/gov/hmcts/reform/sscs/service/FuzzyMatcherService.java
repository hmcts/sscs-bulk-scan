package uk.gov.hmcts.reform.sscs.service;

import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;
import static uk.gov.hmcts.reform.sscs.ccd.domain.Benefit.*;
import static uk.gov.hmcts.reform.sscs.ccd.domain.Benefit.findBenefitByDescription;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.ExtractedResult;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.Benefit;

@Service
@Slf4j
public class FuzzyMatcherService {
    private static final int THRESHOLD_SCORE = 90;

    private static final Map<String, Benefit> FUZZY_WORD_MATCH_MAP = Map.of(
        "personal", PIP,
        "independence", PIP,
        "universal", UC,
        "employment", ESA
    );

    private static final Map<String, Benefit> EXACT_WORD_MATCH = Map.ofEntries(
        Map.entry("AA", ATTENDANCE_ALLOWANCE),
        Map.entry("IS", INCOME_SUPPORT),
        Map.entry("liv", DLA),
        Map.entry("SF", SOCIAL_FUND),
        Map.entry("PC", PENSION_CREDITS),
        Map.entry("RP", RETIREMENT_PENSION),
        Map.entry("BB", BEREAVEMENT_BENEFIT),
        Map.entry("MA", MATERNITY_ALLOWANCE),
        Map.entry("IIDB", IIDB),
        Map.entry("BSPS", BEREAVEMENT_SUPPORT_PAYMENT_SCHEME),
        Map.entry("BSPA", BEREAVEMENT_SUPPORT_PAYMENT_SCHEME)
    );

    private final List<String> fuzzyChoices;

    public FuzzyMatcherService() {
        List<String> allMatches = stream(Benefit.values()).flatMap(benefit -> Stream.of(benefit.getShortName(), benefit.getDescription())).collect(Collectors.toList());
        allMatches.addAll(FUZZY_WORD_MATCH_MAP.keySet());
        fuzzyChoices = Collections.unmodifiableList(allMatches);
    }

    public String matchBenefitType(String ocrBenefitValue) {
        return getBenefitByCode(ocrBenefitValue).map(Benefit::getShortName).orElse(ocrBenefitValue);
    }

    private Optional<Benefit> getBenefitByCode(String code) {
        final ExtractedResult extractedResult = FuzzySearch.extractOne(code.replaceAll("\\.",""), fuzzyChoices);
        logMessage(code, extractedResult);
        String fuzzyCode = getSearchCodeBasedOnScoreThreshold(code, extractedResult);
        return getBenefit(fuzzyCode);
    }

    private String getSearchCodeBasedOnScoreThreshold(String code, ExtractedResult extractedResult) {
        return (extractedResult.getScore() < THRESHOLD_SCORE) ? code : extractedResult.getString();
    }

    private void logMessage(String code, ExtractedResult extractedResult) {
        log.info("Search code {} has a fuzzy match score of {} with {}. The threshold score is {}. {} the fuzzy match.",
            code, extractedResult.getScore(), extractedResult.getString(), THRESHOLD_SCORE,
            extractedResult.getScore() < THRESHOLD_SCORE ? "Not Using" : "Using");
    }

    private Optional<Benefit> getBenefit(String fuzzyCode) {
        return ofNullable(findBenefitByShortName(fuzzyCode)
            .orElseGet(() -> findBenefitByDescription(fuzzyCode)
                    .orElseGet(() -> findBenefitByFuzzyExactWord(fuzzyCode)
                    .orElseGet(() -> findBenefitByExactWord(fuzzyCode)
                        .orElse(null)))));
    }

    private static Optional<Benefit> findBenefitByFuzzyExactWord(String code) {
        return FUZZY_WORD_MATCH_MAP.keySet().stream()
            .filter(key -> key.equalsIgnoreCase(code))
            .findFirst()
            .map(FUZZY_WORD_MATCH_MAP::get);
    }

    private static Optional<Benefit> findBenefitByExactWord(String code) {
        return EXACT_WORD_MATCH.keySet().stream()
            .filter(key -> key.equalsIgnoreCase(code.replaceAll("\\.", "")))
            .findFirst()
            .map(EXACT_WORD_MATCH::get);
    }

}
