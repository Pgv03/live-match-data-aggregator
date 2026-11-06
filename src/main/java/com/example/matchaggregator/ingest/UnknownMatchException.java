package com.example.matchaggregator.ingest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UnknownMatchException extends RuntimeException {
    public UnknownMatchException(String message) { super(message); }
}