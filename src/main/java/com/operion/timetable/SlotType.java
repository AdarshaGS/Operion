package com.operion.timetable;

/** What a period is for. Only TEACHING periods hold timetable entries; the other two are rendered as full-width rows. */
public enum SlotType {
	TEACHING,
	/** A rest or meal break. Exempt from the minimum gap between teaching periods on both sides. */
	BREAK,
	/** A whole-school activity such as assembly or games. Also exempt from the gap and never holds an entry. */
	SPECIAL;

	public boolean isTeaching() {
		return this == TEACHING;
	}
}
