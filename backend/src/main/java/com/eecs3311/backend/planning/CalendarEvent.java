package com.eecs3311.backend.planning;

import java.time.Instant;
import java.util.UUID;

public record CalendarEvent(UUID eventId, UUID ownerId, UUID courseId, UUID planId, String type, String title, Instant startTime, Instant endTime) { }
