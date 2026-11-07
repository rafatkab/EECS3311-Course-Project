package com.eecs3311.backend.planning;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanPersistence {
    private final PlanningRepository repository;
    public PlanPersistence(PlanningRepository repository) { this.repository = repository; }
    @Transactional public StudyPlan save(StudyPlan plan, Calendar calendar) {
        repository.savePlan(plan);
        plan.attach(calendar);
        try { plan.notifyObservers(); } finally { plan.detach(calendar); }
        return plan;
    }
}
