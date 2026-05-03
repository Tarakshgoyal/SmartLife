package com.smartlife.service;

import com.smartlife.model.HealthLog;
import com.smartlife.repository.HealthLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final HealthLogRepository healthLogRepository;

    public Page<HealthLog> getLogs(Long userId, Pageable pageable) {
        return healthLogRepository.findByUserId(userId, pageable);
    }

    public List<HealthLog> getLogsInRange(Long userId, LocalDate from, LocalDate to) {
        return healthLogRepository.findByUserIdAndDateBetween(userId, from, to);
    }

    public HealthLog create(Long userId, HealthLog req) {
        req.setUserId(userId);
        if (req.getDate() == null) req.setDate(LocalDate.now());
        return healthLogRepository.save(req);
    }

    public HealthLog update(Long id, Long userId, HealthLog req) {
        HealthLog existing = healthLogRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Health log not found"));
        if (req.getDate() != null) existing.setDate(req.getDate());
        if (req.getSteps() != null) existing.setSteps(req.getSteps());
        if (req.getSleepHours() != null) existing.setSleepHours(req.getSleepHours());
        if (req.getHeartRate() != null) existing.setHeartRate(req.getHeartRate());
        if (req.getCalories() != null) existing.setCalories(req.getCalories());
        if (req.getNotes() != null) existing.setNotes(req.getNotes());
        return healthLogRepository.save(existing);
    }

    public void delete(Long id, Long userId) {
        HealthLog log = healthLogRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Health log not found"));
        healthLogRepository.delete(log);
    }

    public Map<String, Object> getInsights(Long userId, int days) {
        LocalDate from = LocalDate.now().minusDays(days);
        List<HealthLog> logs = healthLogRepository.findByUserIdAndDateAfter(userId, from);

        OptionalDouble avgSteps = logs.stream()
                .filter(l -> l.getSteps() != null)
                .mapToInt(HealthLog::getSteps).average();
        OptionalDouble avgSleep = logs.stream()
                .filter(l -> l.getSleepHours() != null)
                .mapToDouble(HealthLog::getSleepHours).average();
        OptionalDouble avgHR = logs.stream()
                .filter(l -> l.getHeartRate() != null)
                .mapToInt(HealthLog::getHeartRate).average();
        OptionalDouble avgCal = logs.stream()
                .filter(l -> l.getCalories() != null)
                .mapToInt(HealthLog::getCalories).average();

        Map<String, Object> result = new HashMap<>();
        result.put("averageSteps", avgSteps.isPresent() ? Math.round(avgSteps.getAsDouble()) : 0);
        result.put("averageSleep", avgSleep.isPresent() ? Math.round(avgSleep.getAsDouble() * 10.0) / 10.0 : 0);
        result.put("averageHeartRate", avgHR.isPresent() ? Math.round(avgHR.getAsDouble()) : 0);
        result.put("averageCalories", avgCal.isPresent() ? Math.round(avgCal.getAsDouble()) : 0);
        return result;
    }
}
