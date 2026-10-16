package com.operion.common;

import java.util.Arrays;

/** Parses a raw request string against an enum as a 400 - plain {@code valueOf} would surface as a 404 naming the internal enum class. */
public final class EnumParam {

	private EnumParam() {
	}

	public static <E extends Enum<E>> E parse(Class<E> type, String value) {
		if (value != null) {
			for (E constant : type.getEnumConstants()) {
				if (constant.name().equals(value)) {
					return constant;
				}
			}
		}
		throw new InvalidRequestException("Invalid value" + (value == null ? "" : " '" + value + "'") + "; expected one of " + Arrays.toString(type.getEnumConstants()));
	}
}
