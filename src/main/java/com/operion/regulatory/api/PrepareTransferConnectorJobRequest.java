package com.operion.regulatory.api;

public record PrepareTransferConnectorJobRequest(Long studentExitId, Long schemaId, String connectorName) {
}
