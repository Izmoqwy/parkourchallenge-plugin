package eu.izmoqwy.parkourchallenge;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class TimeFormatter {

    private static final DateFormat minutesDateFormat = new SimpleDateFormat("mm'm' ss's'. SSS'ms'"),
            secondsDateFormat = new SimpleDateFormat("ss's'. SSS'ms'");

    private TimeFormatter() {
        throw new UnsupportedOperationException();
    }

    public static String fromMillis(long millis) {
        if (millis >= 60 * 1000)
            return minutesDateFormat.format(millis);
        return secondsDateFormat.format(millis);
    }

}
