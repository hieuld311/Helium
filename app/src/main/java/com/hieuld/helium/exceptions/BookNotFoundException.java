package com.hieuld.helium.exceptions;

public class BookNotFoundException extends BookLoadException {
    private static final long serialVersionUID = 1;

    public BookNotFoundException(Throwable th) {
        super(th);
    }

    public BookNotFoundException() {
    }
}
