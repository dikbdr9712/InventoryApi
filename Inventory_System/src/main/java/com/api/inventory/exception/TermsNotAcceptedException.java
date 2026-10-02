package com.api.inventory.exception;

/**
 * A seller or driver must accept the current version of their agreement first.
 * Answered with 428 "Precondition Required" and the agreement details, so the dashboard can show it.
 */
public class TermsNotAcceptedException extends RuntimeException {

    private final String termsType;
    private final Integer version;
    private final String title;

    public TermsNotAcceptedException(String termsType, Integer version, String title) {
        super("Please read and accept the latest " + title + " (version " + version + ") to continue.");
        this.termsType = termsType;
        this.version = version;
        this.title = title;
    }

    public String getTermsType() { return termsType; }
    public Integer getVersion() { return version; }
    public String getTitle() { return title; }
}
