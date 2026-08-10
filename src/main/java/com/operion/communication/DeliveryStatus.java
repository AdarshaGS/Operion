package com.operion.communication;

/**
 * PENDING/FAILED are unreached by today's IN_APP-only fan-out (row creation IS delivery
 * for that channel) - reserved for when EMAIL/SMS need an actual async dispatch step.
 */
public enum DeliveryStatus {
	PENDING,
	SENT,
	FAILED,
	READ
}
