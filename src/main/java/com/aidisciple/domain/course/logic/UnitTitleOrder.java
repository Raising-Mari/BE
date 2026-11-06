package com.aidisciple.domain.course.logic;

/**
 * 주차 이름을 사람이 읽는 순서로 비교한다. 숫자는 값으로 비교해서 "2주차"가 "10주차"보다 앞에 온다.
 * 글자 순서로 정렬하면 "10주차"가 "2주차"보다 앞서기 때문에 필요하다.
 */
public final class UnitTitleOrder {

	private UnitTitleOrder() {
	}

	public static int compare(String a, String b) {
		int i = 0;
		int j = 0;
		while (i < a.length() && j < b.length()) {
			char x = a.charAt(i);
			char y = b.charAt(j);
			if (isDigit(x) && isDigit(y)) {
				int iEnd = digitsEnd(a, i);
				int jEnd = digitsEnd(b, j);
				String numberA = stripLeadingZeros(a.substring(i, iEnd));
				String numberB = stripLeadingZeros(b.substring(j, jEnd));
				if (numberA.length() != numberB.length()) {
					return Integer.compare(numberA.length(), numberB.length());
				}
				int byDigits = numberA.compareTo(numberB);
				if (byDigits != 0) {
					return byDigits;
				}
				i = iEnd;
				j = jEnd;
			} else {
				int byChar = Character.compare(Character.toLowerCase(x), Character.toLowerCase(y));
				if (byChar != 0) {
					return byChar;
				}
				i++;
				j++;
			}
		}
		return Integer.compare(a.length() - i, b.length() - j);
	}

	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private static int digitsEnd(String s, int start) {
		int end = start;
		while (end < s.length() && isDigit(s.charAt(end))) {
			end++;
		}
		return end;
	}

	private static String stripLeadingZeros(String digits) {
		int start = 0;
		while (start < digits.length() && digits.charAt(start) == '0') {
			start++;
		}
		return digits.substring(start);
	}

}
