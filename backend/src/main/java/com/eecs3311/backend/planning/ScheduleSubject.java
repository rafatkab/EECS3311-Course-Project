package com.eecs3311.backend.planning;

public interface ScheduleSubject {
    void attach(ScheduleObserver observer);
    void detach(ScheduleObserver observer);
    void notifyObservers();
}
