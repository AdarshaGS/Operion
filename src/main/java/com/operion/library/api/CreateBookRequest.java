package com.operion.library.api;

public record CreateBookRequest(String isbn, String title, String author, String publisher, String category, String edition) {
}
