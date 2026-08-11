package com.operion.identity.api;

public record CreateUserRequest(String email, String phone, String password) {
}
