package uk.gov.hmcts.reform.sscs.service;

import static java.util.Arrays.stream;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.apache.commons.lang3.RegExUtils.replaceAll;
import static org.apache.commons.lang3.StringUtils.lowerCase;
import static org.apache.commons.lang3.StringUtils.rightPad;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;
import static uk.gov.hmcts.reform.sscs.ccd.domain.Benefit.*;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.BoundExtractedResult;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.Benefit;

@Service
@Slf4j
public class FuzzyMatcherService {
    private static final int THRESHOLD_MATCH_SCORE = 90;
    private static final int MAX_FUZZY_SEARCH_LENGTH = 4;

    private static final List<String> EXACT_WORDS_THAT_WILL_NOT_CAUSE_A_MATCH = List.of(
        "support",
        "allowance",
        "benefit",
        "pension",
        ""
    );

    private static final Set<Pair<String, Benefit>> EXACT_WORDS_THAT_WILL_CAUSE_A_MATCH =
        ImmutableSet.<Pair<String, Benefit>>builder()
            .addAll(
                Set.of(
                    Pair.of("AA", ATTENDANCE_ALLOWANCE),
                    Pair.of("IS", INCOME_SUPPORT),
                    Pair.of("SF", SOCIAL_FUND),
                    Pair.of("PC", PENSION_CREDITS),
                    Pair.of("RP", RETIREMENT_PENSION),
                    Pair.of("BB", BEREAVEMENT_BENEFIT),
                    Pair.of("CA", CARERS_ALLOWANCE),
                    Pair.of("MA", MATERNITY_ALLOWANCE),
                    Pair.of("IIDB", IIDB),
                    Pair.of("BSPS", BEREAVEMENT_SUPPORT_PAYMENT_SCHEME),
                    Pair.of("Credit", UC),
                    Pair.of("IDB", INDUSTRIAL_DEATH_BENEFIT)
                ))
            .addAll(addBenefitShortNamesThatHaveAcronyms())
            .build();

    private static final Set<Pair<String, Benefit>> FUZZY_CHOICES =
        ImmutableSet.<Pair<String, Benefit>>builder()
            .addAll(getBenefitShortNameAndDescriptionFuzzyChoices())
            .addAll(
                Set.of(
                    Pair.of("personal", PIP),
                    Pair.of("independence", PIP),
                    Pair.of("universal", UC),
                    Pair.of("employment", ESA),
                    Pair.of("attendance", ATTENDANCE_ALLOWANCE),
                    Pair.of("disability", DLA),
                    Pair.of("living", DLA),
                    Pair.of("income", INCOME_SUPPORT),
                    Pair.of("death", INDUSTRIAL_DEATH_BENEFIT),
                    Pair.of("retirement", RETIREMENT_PENSION),
                    Pair.of("injuries", IIDB),
                    Pair.of("disablement", IIDB),
                    Pair.of("job", JSA),
                    Pair.of("seeker", JSA),
                    Pair.of("seeker's", JSA),
                    Pair.of("social", SOCIAL_FUND),
                    Pair.of("fund", SOCIAL_FUND),
                    Pair.of("carer's", CARERS_ALLOWANCE),
                    Pair.of("carers", CARERS_ALLOWANCE),
                    Pair.of("maternity", MATERNITY_ALLOWANCE)
                ))
            .build()
            .stream()
            .filter(pair -> pair.getLeft().length() >= MAX_FUZZY_SEARCH_LENGTH)
            .collect(toUnmodifiableSet());

    public String matchBenefitType(String caseId, String ocrBenefitValue) {
        return wordExcludedFromFuzzySearch(caseId, ocrBenefitValue)
            .flatMap(code -> benefitByExactMatchOrFuzzySearch(caseId, code))
            .map(Benefit::getShortName)
            .orElse(ocrBenefitValue);
    }

    public Optional<Benefit> benefitSearch(String caseId, String ocrBenefitValue) {
        return wordExcludedFromFuzzySearch(caseId, ocrBenefitValue)
            .flatMap((code) -> benefitByExactMatchOrFuzzySearch(caseId, code));
    }

    private Optional<Benefit> benefitByExactMatchOrFuzzySearch(String caseId, String code) {
        return benefitByExactMatchSearch(code)
            .or(() -> benefitByFuzzySearch(caseId, code));
    }

    private Optional<Benefit> benefitByExactMatchSearch(String code) {
        return findBenefitByShortName(code)
            .or(() -> findBenefitByDescription(code))
            .or(() -> findBenefitByExactWord(code));
    }

    private Optional<Benefit> benefitByFuzzySearch(String caseId, String code) {
        final BoundExtractedResult<Pair<String, Benefit>> result = runFuzzySearch(code);
        logMessage(caseId, code, result);
        return benefitBasedOnThreshold(result);
    }

    private BoundExtractedResult<Pair<String, Benefit>> runFuzzySearch(String code) {
        return FuzzySearch.extractOne(code, FUZZY_CHOICES, Pair::getLeft);
    }

    private Optional<String> wordExcludedFromFuzzySearch(String caseId, String code) {
        String searchCode = stripToEmpty(stripNonAlphaNumeric(code));
        boolean match = EXACT_WORDS_THAT_WILL_NOT_CAUSE_A_MATCH.contains(lowerCase(searchCode));
        logMessageIfExcludedFromFuzzySearch(caseId, code, match);
        return match ? empty() : Optional.of(searchCode);
    }

    private void logMessageIfExcludedFromFuzzySearch(String caseId, String code, boolean match) {
        if (match) {
            log.info("The word '{}' has matched the unknown word list. Cannot work out the benefit for caseId {}.", code, caseId);
        }
    }

    private static String stripNonAlphaNumeric(String code) {
        return replaceAll(code, "[^A-Za-z0-9 ]", "");
    }

    private Optional<Benefit> benefitBasedOnThreshold(BoundExtractedResult<Pair<String, Benefit>> extractedResult) {
        return (extractedResult.getScore() < THRESHOLD_MATCH_SCORE)
            ? empty() : Optional.of(extractedResult.getReferent().getRight());
    }

    private void logMessage(String caseId, String code, BoundExtractedResult<Pair<String, Benefit>> result) {
        log.info("Search code '{}' has a fuzzy match score of {} with '{}'. The threshold score is {}. {} the fuzzy match for caseId {}.",
            code, result.getScore(), result.getString(), THRESHOLD_MATCH_SCORE,
            result.getScore() < THRESHOLD_MATCH_SCORE ? "Not Using" : "Using", caseId);
    }

    private static Optional<Benefit> findBenefitByExactWord(String code) {
        return EXACT_WORDS_THAT_WILL_CAUSE_A_MATCH.stream()
            .filter(pair -> pair.getLeft().equalsIgnoreCase(code))
            .findFirst()
            .map(Pair::getRight);
    }

    private static Set<Pair<String, Benefit>> addBenefitShortNamesThatHaveAcronyms() {
        return stream(Benefit.values())
            .filter(Benefit::isHasAcronym)
            .map(b -> Pair.of(b.getShortName(), b))
            .collect(toUnmodifiableSet());
    }

    private static Set<Pair<String, Benefit>> getBenefitShortNameAndDescriptionFuzzyChoices() {
        return stream(values())
            .flatMap(benefit -> Stream.of(
                Pair.of(rightPad(benefit.getShortName(), MAX_FUZZY_SEARCH_LENGTH), benefit),
                Pair.of(rightPad(benefit.getDescription(), MAX_FUZZY_SEARCH_LENGTH), benefit)
            ))
            .collect(toUnmodifiableSet());
    }

}
