package com.operion.identity.api;

public record SubmitProfileChangeRequest(String phone, String email, String photoUrl) {
}
