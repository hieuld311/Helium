package com.hieuld.helium.exceptions;

public class BookLoadException extends Exception {
    private static final long serialVersionUID = 1;

    public BookLoadException() {
    }

    public BookLoadException(String str) {
        super(str);
    }

    public BookLoadException(String str, Throwable th) {
        super(str);
        initCause(th);
    }

    public BookLoadException(Throwable th) {
        initCause(th);
    }
}
