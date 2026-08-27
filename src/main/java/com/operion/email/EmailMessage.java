package com.operion.email;

public record EmailMessage(String to, String subject, String htmlBody) {
}
