package com.eecs3311.backend.planning;

import com.eecs3311.backend.ai.AiFailure;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/** Owner-level coordination prevents profile/calendar writes and different course plans racing. */
@Component
public class PlanningLock {
    private final ReentrantLock[] locks = IntStream.range(0, 64).mapToObj(i -> new ReentrantLock()).toArray(ReentrantLock[]::new);
    public <T> T run(UUID owner, Supplier<T> operation) {
        var lock = locks[Math.floorMod(owner.hashCode(), locks.length)];
        if (!lock.tryLock()) throw new AiFailure(409, "Study planning is busy for this student. Try again shortly.");
        try { return operation.get(); } finally { lock.unlock(); }
    }
}
