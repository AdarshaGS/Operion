package com.operion.hr;

import java.util.HashSet;
import java.util.Set;

import com.operion.authorization.HrProfileLookup;
import org.springframework.stereotype.Component;

/** HR's side of {@link HrProfileLookup} - core authorization can't depend on this package, so it asks through the interface. */
@Component
public class StaffProfileLookup implements HrProfileLookup {

	private final StaffProfileRepository staffProfileRepository;

	public StaffProfileLookup(StaffProfileRepository staffProfileRepository) {
		this.staffProfileRepository = staffProfileRepository;
	}

	@Override
	public Set<Long> personIdsWithProfile() {
		return new HashSet<>(staffProfileRepository.findAllPersonIds());
	}

	@Override
	public boolean hasProfile(Long personId) {
		return staffProfileRepository.existsByPersonId(personId);
	}
}
