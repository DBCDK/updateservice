package dk.dbc.common.records;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @brief Implements a reader to read a marc record in line format to construct
 * a MarcRecord instance.
 */
public class MarcRecordFactory {
    public static final String FIELD_PATTERN = "((\\d|\\w){3})\\s((\\s|\\d){2}).*";

    private MarcRecordFactory() {
    }

    public static MarcRecord readRecord(String str) {
        String[] list = str.split("\n");
        ArrayList<MarcField> fields = new ArrayList<>();
        for (String s : list) {
            s = s.trim();
            if (s.matches(FIELD_PATTERN)) {
                fields.add(readField(s));
            }
        }
        return new MarcRecord(fields);
    }

    public static MarcField readField(String str) {
        if (str == null) {
            return null;
        }

        Pattern p = Pattern.compile(FIELD_PATTERN);
        Matcher m = p.matcher(str);

        if (!m.matches()) {
            return null;
        }

        int index = str.indexOf('*');
        if (index == -1) {
            return new MarcField(m.group(1), m.group(3), new ArrayList<>());
        }

        ArrayList<MarcSubField> subfields = new ArrayList<>();
        String[] list = str.substring(index).split("(?=(\\*[^\\*@]))");
        for (String s : list) {
            s = s.trim();
            if (s.startsWith("*")) {
                subfields.add(readSubField(s));
            }
        }
        return new MarcField(m.group(1), m.group(3), subfields);
    }

    public static MarcSubField readSubField(String str) {
        if (str == null) {
            return null;
        }
        if (str.length() < 2) {
            return null;
        }
        return new MarcSubField(String.valueOf(str.charAt(1)), str.substring(2).replace("@*", "*").trim());
    }
}
