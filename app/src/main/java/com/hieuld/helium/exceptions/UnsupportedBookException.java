package com.hieuld.helium.exceptions;

public class UnsupportedBookException extends BookLoadException {
    private static final long serialVersionUID = 1;

    public UnsupportedBookException(String str) {
        super(str);
    }
}
