package uk.gov.hmcts.reform.sscs.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang.BooleanUtils;

public final class SscsOcrDataUtil {

    private static final String YES = "Yes";
    private static final String NO = "No";

    private SscsOcrDataUtil() {

    }

    public static Boolean hasPerson(Map<String, Object> pairs, String person) {

        return findBooleanExists(getField(pairs,person + "_title"), getField(pairs,person + "_first_name"),
            getField(pairs,person + "_last_name"), getField(pairs,person + "_address_line1"), getField(pairs,person + "_address_line2"),
            getField(pairs,person + "_address_line3"), getField(pairs,person + "_address_line4"), getField(pairs,person + "_postcode"),
            getField(pairs,person + "_dob"), getField(pairs,person + "_nino"),  getField(pairs,person + "_company"));
    }

    public static boolean findBooleanExists(String... values) {
        for (String v : values) {
            if (v != null) {
                return true;
            }
        }
        return false;
    }

    public static String getField(Map<String, Object> pairs, String field) {
        return pairs.containsKey(field) ? pairs.get(field).toString() : null;
    }

    public static boolean doValuesContradict(Map<String, Object> pairs, List<String> errors, String value1, String value2) {
        if (pairs.get(value1).equals(pairs.get(value2))) {
            errors.add(value1 + " and " + value2 + " have contradicting values");
            return true;
        }
        return false;
    }

    public static boolean areBooleansValid(Map<String, Object> pairs, List<String> errors, String... values) {
        return Stream.of(values).allMatch(value -> {
            if (pairs.get(value) != null && BooleanUtils.toBooleanObject(pairs.get(value).toString()) != null) {
                return true;
            }
            errors.add(value + " does not contain a valid boolean value. Needs to be true or false");
            return false;
        });
    }

    public static String convertBooleanToYesNoString(boolean value) {
        return value ? YES : NO;
    }

    public static String generateDateForCcd(Map<String, Object> pairs, List<String> errors, String field) {
        if (pairs.containsKey(field)) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

            try {
                return LocalDate.parse(getField(pairs, field), formatter).toString();
            } catch (DateTimeParseException ex) {
                errors.add(field + " is an invalid date field. Needs to be in the format dd/mm/yyyy");
            }
        }
        return null;
    }

}
