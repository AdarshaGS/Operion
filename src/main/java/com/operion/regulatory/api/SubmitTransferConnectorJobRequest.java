package com.operion.regulatory.api;

/** externalReference is optional - whatever the receiving system handed back. */
public record SubmitTransferConnectorJobRequest(String externalReference) {
}
