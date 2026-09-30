package com.operion.privacy.api;

/** code is only read on create - it is the fixed identity of a purpose. */
public record ConsentPurposeRequest(String code, String name, String description) {
}
