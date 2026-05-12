package com.hieuld.helium.exceptions;

public class MalformedBookException extends BookLoadException {
    private static final long serialVersionUID = 1;

    public MalformedBookException() {
    }

    public MalformedBookException(String str) {
        super(str);
    }

    public MalformedBookException(String str, Throwable th) {
        super(str);
        initCause(th);
    }

    public MalformedBookException(Throwable th) {
        initCause(th);
    }
}
