/*
 * (C) Copyright IBM Corp. 2020, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.linuxforhealth.hl7.data;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringTokenizer;
import org.hl7.fhir.r4.model.codesystems.EncounterStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import io.github.linuxforhealth.hl7.data.date.DateUtil;

public class Hl7RelatedGeneralUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Hl7RelatedGeneralUtils.class);
    
    private Hl7RelatedGeneralUtils() {
    }

    // IMPORTANT NOTES about Extracting the low and high number.  Description of these numbers is:
    // "When the observation quantifies the amount of a toxic substance, then the upper limit of the range identifies the toxic limit. 
    // If the observation quantifies a drug, the lower limits identify the lower therapeutic bounds and the upper limits represent 
    // the upper therapeutic bounds above which toxic side effects are common.
    // We don't know if it's a toxic substance or a drug, but we understand that:
    //  1. Two numbers means we have lower and upper limits.
    //  2. One number means only the upper limit.
    // NOTE: the current implementation does not pay attention to >, <, -, or other notation.  Just a first (and second) decimal number.
    // Thus ">0.50" "<0.50" and "-0.50" are all treated the same and a high number is found.
    // Thus "0.50-2.50" "0.50 2.50" "<0.50 < 2.50" and ">0.50 > 2.50" are all the same, the first is low, the second is high.
    // This may need to be changed in the future if we find actual data is different.

    // This regex extracts 2 sets of numbers (including decimal points) from a string.  
    // For example, from the string "1.1 mL - 1.5 mL" the first group is 1.1, and the second group is 1.5
    // This regex has any number of non-numeric (non greedy), a number, characters, a number, optional additional characters.
    // ^  beginning of line 
    // \D*? any number of non-numeric characters (non greedy)
    // ([\\d.]+) capture as first group a set of digits and period (at least one digit required)
    // \D* any number of non-numeric characters
    // ([\\d.]+) capture as second group a set of digits and period (will be empty if not found)
    // .* any number of characters
    private static final String REGEX_FIRST_TWO_NUMBERS_AMID_OTHER_TEXT = "^\\D*?([\\d.]+)\\D*([\\d.]*).*";
    // Compile this into a pattern for reuse
    private static final Pattern PATTERN_FIRST_TWO_NUMBERS_AMID_OTHER_TEXT = Pattern.compile(REGEX_FIRST_TWO_NUMBERS_AMID_OTHER_TEXT);

    // ExtractLow - see comments above
    public static String extractLow(Object input) {
        String val = Hl7DataHandlerUtil.getStringValue(input);
        if (StringUtils.isNotBlank(val)) {
            Matcher m = PATTERN_FIRST_TWO_NUMBERS_AMID_OTHER_TEXT.matcher(val);

            // Only the low (first) number if there is also a high (second) number
            if (m.find() && !m.group(2).isEmpty()){
                    return m.group(1);
            } 
            return null;
        }
        return null;
    }

    // ExtractHigh - see comments above
    public static String extractHigh(Object input) {
        String val = Hl7DataHandlerUtil.getStringValue(input);
        if (StringUtils.isNotBlank(val)) {
            Matcher m = PATTERN_FIRST_TWO_NUMBERS_AMID_OTHER_TEXT.matcher(val);

            // If one number, it is the high.  If two numbers, the second is high.
            if(m.find()) {
                if (!m.group(2).isEmpty()){
                    return m.group(2);
                } else {
                    return m.group(1);   
                }
            }
            return null;
        }
        return null;
    }

    public static String getEncounterStatus(Object var1, Object var2, Object var3) {
        LOGGER.info("Generating encounter status from var1{}, var2 {}, var3 {}", var1, var2, var3);
        EncounterStatus status = EncounterStatus.UNKNOWN;
        if (var1 != null) {
            status = EncounterStatus.FINISHED;
        } else if (var2 != null) {
            status = EncounterStatus.ARRIVED;
        } else if (var3 != null) {
            status = EncounterStatus.CANCELLED;
        }
        return status.toCode();
    }

    public static String generateName(Object prefix, Object first, Object middle, Object family, Object suffix) {
        LOGGER.info("Generating name from  from prefix {}, first {}, family {} ,suffix {}", prefix, first, middle,
                family, suffix);
        StringBuilder sb = new StringBuilder();
        String valprefix = Hl7DataHandlerUtil.getStringValue(prefix);
        String valfirst = Hl7DataHandlerUtil.getStringValue(first);
        String valmiddle = Hl7DataHandlerUtil.getStringValue(middle);
        String valfamily = Hl7DataHandlerUtil.getStringValue(family);
        String valsuffix = Hl7DataHandlerUtil.getStringValue(suffix);

        if (valprefix != null) {

            sb.append(valprefix).append(" ");
        }
        if (valfirst != null) {
            sb.append(valfirst).append(" ");
        }
        if (valmiddle != null) {
            sb.append(valmiddle).append(" ");
        }
        if (valfamily != null) {
            sb.append(valfamily).append(" ");
        }
        if (valsuffix != null) {
            sb.append(valsuffix).append(" ");
        }
        String name = sb.toString();
        if (StringUtils.isNotBlank(name)) {
            return name.trim();
        } else {
            return null;
        }

    }

    /**
     * Returns difference between time2 - time1 in minutes
     * 
     * @param start DateTime
     * @param end DateTime
     * @return Minutes in Long
     */

    public static Long diffDateMin(Object start, Object end) {
        LOGGER.info("Generating time diff in min  from var1 {}, var2 {}", start, end);
        try {
            Temporal date1 = DateUtil.getTemporal(Hl7DataHandlerUtil.getStringValue(start));
            Temporal date2 = DateUtil.getTemporal(Hl7DataHandlerUtil.getStringValue(end));
            LOGGER.info("temporal dates start: {} , end: {} ", date1, date2);
            if (date1 != null && date2 != null) {
                return ChronoUnit.MINUTES.between(date1, date2);
            }
        } catch (UnsupportedTemporalTypeException e) {
            LOGGER.warn("Cannot evaluate time difference for start: {} , end: {} reason {} ", start, end,
                    e.getMessage());
            LOGGER.debug("Cannot evaluate time difference for start: {} , end: {} ", start, end, e);
            return null;
        }
        return null;
    }


    public static String split(Object input, String delimitter, int index) {
        String stringRepVal = Hl7DataHandlerUtil.getStringValue(input);
        if (StringUtils.isNotBlank(stringRepVal)) {
            StringTokenizer stk = new StringTokenizer(stringRepVal, delimitter);
            if (stk.getTokenList().size() > index) {
                return stk.getTokenList().get(index);
            }

        }
        return null;
    }

    public static String concatenateWithChar(Object input, String delimiterChar) {
        // Engine converts the delimiter character to a String; need to fix escaped characters
        // as they become 2 separate characters rather than 1. 
        // Currently handling '\n' specifically, have not found a more general solution.
        String delimiter = delimiterChar;
        if (delimiterChar.equals("\\n")) {
            delimiter = Character.toString('\n');
        }

        return Hl7DataHandlerUtil.getStringValue(input, true, delimiter, false);
    }

    public static List<String> makeStringArray(String... strs) {
        List<String> result = new ArrayList<String>();

        for (String str : strs)
            if (str != null)
                result.add(str);

        return result;
    }

    public static String getAddressUse(String xad7Type, String xad16Temp, String xad17Bad) {
        LOGGER.info("Calculating address Use from XAD.7 {}, XAD.16 {}, XAD.17 {}", xad7Type, xad16Temp, xad17Bad);

        String addressUse = "";
        if (xad16Temp != null && xad16Temp.equalsIgnoreCase("Y")
                || ((xad16Temp == null || xad16Temp.isEmpty())
                        && xad7Type != null && xad7Type.equalsIgnoreCase("C"))) {
            addressUse = "temp";
        } else if (xad17Bad != null && xad17Bad.equalsIgnoreCase("Y")
                || ((xad17Bad == null || xad17Bad.isEmpty())
                        && xad7Type != null && xad7Type.equalsIgnoreCase("BA"))) {
            addressUse = "old";
        } else if (xad7Type != null && xad7Type.equalsIgnoreCase("H")) {
            addressUse = "home";
        } else if (xad7Type != null && (xad7Type.equalsIgnoreCase("B") || xad7Type.equalsIgnoreCase("O"))) {
            addressUse = "work";
        } else if (xad7Type != null && xad7Type.equalsIgnoreCase("BI")) {
            addressUse = "billing";
        }
        return addressUse;
    }

    public static String getAddressType(String xad7Type, String xad18Type) {
        LOGGER.info("Calculating address Type from XAD.7 {}, XAD.18 {}", xad7Type, xad18Type);

        String addressType = "";
        if (xad18Type != null && xad18Type.equalsIgnoreCase("M")
                || (xad18Type == null || xad18Type.isEmpty())
                        && (xad7Type != null && xad7Type.equalsIgnoreCase("M"))) {
            addressType = "postal";
        } else if (xad18Type != null && xad18Type.equalsIgnoreCase("V")
                || (xad18Type == null || xad18Type.isEmpty())
                        && (xad7Type != null && xad7Type.equalsIgnoreCase("SH"))) {
            addressType = "physical";
        }
        return addressType;
    }

    /**
     * Special rules for FHIR Address District. From 18.173.1 build.fhir.org Segment
     * PID to Patient Map. If the address has a County/Parish code, use it. However,
     * if it is empty, and there is exactly one address (repeated) use the PID
     * county. PID-12 maps to patient.address.District "IF PID-11 LST.COUNT EQUALS 1
     * AND PID-11.9 IS NOT VALUED"
     */
    public static String getAddressDistrict(String patientCountyPid12, String addressCountyParishPid119,
            Object patient) {
        LOGGER.info("getAddressCountyParish for {}", patient);

        String returnDistrict = addressCountyParishPid119;
        if (returnDistrict == null) {
            Segment pidSegment = (Segment) patient;
            try {
                Type[] addresses = pidSegment.getField(11);
                if (addresses.length == 1) {
                    returnDistrict = patientCountyPid12;
                }

            } catch (HL7Exception e) {
                // Do nothing. Just eat the error.
                // Let the initial value stand
            }

        }

        return returnDistrict;
    }



    // Takes all the pieces of ContactPoint/telecom number from XTN, formats to a user friendly
    // ContactPoint/Telecom number based on rules documented in the steps
    public static String getFormattedTelecomNumberValue(String xtn1Old, String xtn5Country, String xtn6Area,
            String xtn7Local, String xtn8Extension, String xtn12Unformatted) {
        String returnValue = "";

        // If the local number exists...
        if (xtn7Local != null && xtn7Local.length() > 0) {
            returnValue = formatCountryAndArea(xtn5Country, xtn6Area) + formatLocalNumber(xtn7Local);
            // If an extention exists with any form of local number, append a space the
            // extension abbreviation
            if (xtn8Extension != null && xtn8Extension.length() > 0) {
                returnValue = returnValue + " ext. " + xtn8Extension;
            }
            // otherwise if the unformatted number exists, use it
        } else if (xtn12Unformatted != null && xtn12Unformatted.length() > 0) {
            returnValue = xtn12Unformatted;
            // otherwise if the string number exists, use it
        } else if (xtn1Old != null && xtn1Old.length() > 0) {
            returnValue = xtn1Old;
        }
        return returnValue;
    }

    // Private method to split and format the 7 digit local telecom number
    private static String formatLocalNumber(String localNumber) {
        // Split after the 3rd digit, add a space, add the rest of the number
        // Seven digit number will format: 123 4567
        return localNumber.substring(0, 3) + " " + localNumber.substring(3);
    }

    // Private method to format the country and area code
    private static String formatCountryAndArea(String country, String area) {
        String returnValue = "";
        // Only process formatting if there is an area code
        if (area != null && area.length() > 0) {
            if (country != null && country.length() > 0) {
                // If there is a country code, format +22 123 456 7890
                returnValue = "+" + country + " " + area + " ";
            } else {
                // If there is a NOT a country code, format (123) 456 7890
                returnValue = "(" + area + ") ";
            }
        }
        return returnValue;
    }

}
