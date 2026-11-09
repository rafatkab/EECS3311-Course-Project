package com.eecs3311.backend.assessment;

import com.eecs3311.backend.ai.*;
import java.time.Duration;

/** Per-submission state; never shared between students or grading requests. */
public final class GradingBudget {
    private final long deadline = System.nanoTime()+Duration.ofSeconds(180).toNanos();
    private int calls;
    private long inputTokens, outputTokens;
    public Duration timeout(AiSettings settings) {
        long remaining=deadline-System.nanoTime();
        if (remaining<=0 || calls>=40) throw new AiFailure(504,"Grading exceeded its time limit. No grade was saved; your attempt remains unchanged.");
        return Duration.ofNanos(Math.min(remaining,Duration.ofSeconds(settings.timeoutSeconds()).toNanos()));
    }
    public void record(AIEngine.Completion completion) {
        calls++; inputTokens=sum(inputTokens,completion.inputTokens()); outputTokens=sum(outputTokens,completion.outputTokens());
    }
    private static long sum(long a,long b) { return a<0 || b<0 ? -1 : a+b; }
    public int calls() { return calls; }
    public long inputTokens() { return inputTokens; }
    public long outputTokens() { return outputTokens; }
}
