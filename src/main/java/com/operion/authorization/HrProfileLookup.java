package com.operion.authorization;

import java.util.Set;

/**
 * Lets core membership code ask "does this person also have an HR profile?" without core
 * depending on the HR vertical (see ArchitectureBoundaryTest) - the HR module supplies the
 * implementation. Backs the {@code hasStaffProfile} flag on the Members list.
 */
public interface HrProfileLookup {

	/** One query for every person in the current org who has a staff profile (any status, including staff who have left). */
	Set<Long> personIdsWithProfile();

	boolean hasProfile(Long personId);
}
