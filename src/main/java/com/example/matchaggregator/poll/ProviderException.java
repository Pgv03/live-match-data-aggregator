package com.example.matchaggregator.poll;

/** Any failure to get a usable response from a provider. */
public class ProviderException extends RuntimeException {
    public ProviderException(String message) { super(message); }
    public ProviderException(String message, Throwable cause) { super(message, cause); }
}