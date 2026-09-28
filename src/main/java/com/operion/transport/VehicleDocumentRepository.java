package com.operion.transport;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, Long> {

	List<VehicleDocument> findByVehicleIdAndStatus(Long vehicleId, VehicleDocumentStatus status);

	Optional<VehicleDocument> findByVehicleIdAndDocumentTypeAndStatus(Long vehicleId, String documentType, VehicleDocumentStatus status);
}
