package com.operion.common;

import com.operion.common.persistence.PlatformPlane;
import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface TimezoneRepository extends JpaRepository<Timezone, Long> {
}
