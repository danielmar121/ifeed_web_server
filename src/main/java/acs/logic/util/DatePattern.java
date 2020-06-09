package acs.logic.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;

public class DatePattern {
	final static String pattern = "EEE MMM d HH:mm:ss zzz yyyy";
	final static String UTC_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	final static String dateOnly = "yyyy-MM-dd";

	public static boolean isDate(String dateStr) {
		SimpleDateFormat sdf = new SimpleDateFormat(UTC_PATTERN);
		try {
			sdf.parse(dateStr);
		} catch (ParseException e) {
			try {
				sdf.applyPattern(pattern);
				sdf.parse(dateStr);
			} catch (ParseException e1) {
				try {
					sdf.applyPattern(dateOnly);
					sdf.parse(dateStr);
				} catch (ParseException e2) {
					return false;
				}
			}
		}
		return true;
	}

}
