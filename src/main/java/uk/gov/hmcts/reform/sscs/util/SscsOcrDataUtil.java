package uk.gov.hmcts.reform.sscs.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

public final class SscsOcrDataUtil {

    private static final String YES = "Yes";
    private static final String NO = "No";

    private SscsOcrDataUtil() {

    }

    public static Boolean hasPerson(Map<String, Object> pairs, String person) {

        return findBooleanExists(getField(pairs,person + "_title"), getField(pairs,person + "_first_name"),
            getField(pairs,person + "_last_name"), getField(pairs,person + "_address_line1"), getField(pairs,person + "_address_line2"),
            getField(pairs,person + "_address_line3"), getField(pairs,person + "_address_line4"), getField(pairs,person + "_postcode"),
            getField(pairs,person + "_dob"), getField(pairs,person + "_nino"),  getField(pairs,person + "_company"),  getField(pairs,person + "_phone"));
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
        return pairs.containsKey(field) && pairs.get(field) != null ? pairs.get(field).toString() : null;
    }

    public static boolean doValuesContradict(Map<String, Object> pairs, Set<String> errors, String value1, String value2) {
        if (pairs.get(value1).equals(pairs.get(value2))) {
            errors.add(value1 + " and " + value2 + " have contradicting values");
            return true;
        }
        return false;
    }

    public static boolean areBooleansValid(Map<String, Object> pairs, Set<String> errors, String... values) {
        return Stream.of(values).allMatch(value -> checkBooleanValue(pairs, errors, value));
    }

    public static boolean checkBooleanValue(Map<String, Object> pairs, Set<String> errors, String value) {
        if (pairs.get(value) != null) {
            Boolean booleanValue = BooleanUtils.toBooleanObject(pairs.get(value).toString()) != null;
            if (booleanValue) {
                return true;
            } else {
                errors.add(value + " has an invalid value. Should be Yes/No or True/False");
            }
        }
        return false;
    }

    public static boolean getBoolean(Map<String, Object> pairs, Set<String> errors, String value) {
        return checkBooleanValue(pairs, errors, value) && BooleanUtils.toBoolean(pairs.get(value).toString());
    }

    public static String convertBooleanToYesNoString(boolean value) {
        return value ? YES : NO;
    }

    public static String generateDateForCcd(Map<String, Object> pairs, Set<String> errors, String fieldName) {
        if (pairs.containsKey(fieldName)) {
            return getDateForCcd(getField(pairs, fieldName), errors, fieldName + " is an invalid date field. Needs to be a valid date and in the format dd/mm/yyyy");
        }
        return null;
    }

    public static String getDateForCcd(String ocrField, Set<String> errors, String errorMessage) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);

        if (!StringUtils.isEmpty(ocrField)) {
            try {
                return LocalDate.parse(ocrField, formatter).toString();
            } catch (DateTimeParseException ex) {
                errors.add(errorMessage);
            }
        }
        return null;
    }

}
