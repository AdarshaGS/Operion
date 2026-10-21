package com.operion.document.pdf;

/** A document could not be rendered - a template or engine fault, never a user input problem. */
public class DocumentRenderException extends RuntimeException {

	public DocumentRenderException(String message, Throwable cause) {
		super(message, cause);
	}
}
