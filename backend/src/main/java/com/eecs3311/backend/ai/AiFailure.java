package com.eecs3311.backend.ai;

/** A safe user-facing error; raw provider errors and credentials are never returned. */
public class AiFailure extends RuntimeException {
    private final int status;
    public AiFailure(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
