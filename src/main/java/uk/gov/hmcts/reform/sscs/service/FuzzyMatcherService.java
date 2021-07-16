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
                    Pair.of("Carers", CARERS_ALLOWANCE),
                    Pair.of("MA", MATERNITY_ALLOWANCE),
                    Pair.of("IIDB", IIDB),
                    Pair.of("BSPS", BEREAVEMENT_SUPPORT_PAYMENT_SCHEME),
                    Pair.of("Credit", UC)
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
                    Pair.of("employment", ESA)
                ))
            .build()
            .stream()
            .filter(pair -> pair.getLeft().length() >= MAX_FUZZY_SEARCH_LENGTH)
            .collect(toUnmodifiableSet());

    public String matchBenefitType(String ocrBenefitValue) {
        return benefitByExactMatchOrFuzzySearch(stripToEmpty(stripNonAlphaNumeric(ocrBenefitValue)))
            .map(Benefit::getShortName)
            .orElse(ocrBenefitValue);
    }

    private Optional<Benefit> benefitByExactMatchOrFuzzySearch(String code) {
        return benefitByExactMatchSearch(code)
            .or(() -> benefitByFuzzySearch(code));
    }

    private Optional<Benefit> benefitByExactMatchSearch(String code) {
        return findBenefitByShortName(code)
            .or(() -> findBenefitByDescription(code))
            .or(() -> findBenefitByExactWord(code));
    }

    private Optional<Benefit> benefitByFuzzySearch(String code) {
        final Optional<BoundExtractedResult<Pair<String, Benefit>>> optionalResult = runFuzzySearchIfTextIsNotExcluded(code);
        optionalResult.ifPresent(result -> logMessage(code, result));
        return optionalResult.flatMap(this::benefitBasedOnThreshold);
    }

    private Optional<BoundExtractedResult<Pair<String, Benefit>>> runFuzzySearchIfTextIsNotExcluded(String code) {
        return isWordExcludedFromFuzzySearch(code)
            ? empty() : Optional.of(FuzzySearch.extractOne(code, FUZZY_CHOICES, Pair::getLeft));
    }

    private boolean isWordExcludedFromFuzzySearch(String code) {
        boolean match = EXACT_WORDS_THAT_WILL_NOT_CAUSE_A_MATCH.contains(lowerCase(code));
        logMessageIfExcludedFromFuzzySearch(code, match);
        return match;
    }

    private void logMessageIfExcludedFromFuzzySearch(String code, boolean match) {
        if (match) {
            log.info("The word '{}' has matched the unknown word list. Cannot work out the benefit.", code);
        }
    }

    private static String stripNonAlphaNumeric(String code) {
        return replaceAll(code, "[^A-Za-z0-9 ]", "");
    }

    private Optional<Benefit> benefitBasedOnThreshold(BoundExtractedResult<Pair<String, Benefit>> extractedResult) {
        return (extractedResult.getScore() < THRESHOLD_MATCH_SCORE)
            ? empty() : Optional.of(extractedResult.getReferent().getRight());
    }

    private void logMessage(String code, BoundExtractedResult<Pair<String, Benefit>> result) {
        log.info("Search code '{}' has a fuzzy match score of {} with '{}'. The threshold score is {}. {} the fuzzy match.",
            code, result.getScore(), result.getString(), THRESHOLD_MATCH_SCORE,
            result.getScore() < THRESHOLD_MATCH_SCORE ? "Not Using" : "Using");
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
