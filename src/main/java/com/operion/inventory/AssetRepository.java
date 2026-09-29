package com.operion.inventory;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {

	List<Asset> findByCampusId(Long campusId);

	Optional<Asset> findByAssetTagIgnoreCase(String assetTag);
}
