package com.smartlife.service;

import com.smartlife.model.Document;
import com.smartlife.model.Expense;
import com.smartlife.repository.DocumentRepository;
import com.smartlife.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final DocumentRepository documentRepository;
    private final ExpenseRepository expenseRepository;

    public Map<String, Object> getDashboard(Long userId) {
        long totalDocuments = documentRepository.countByUserId(userId);
        long totalExpenses = expenseRepository.countByUserId(userId);

        // recent activity: last 5 documents + last 5 expenses merged and sorted
        List<Map<String, Object>> recentActivity = new ArrayList<>();

        List<Document> recentDocs = documentRepository
                .findByUserId(userId, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "uploadedAt")))
                .getContent();
        for (Document d : recentDocs) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("type", "document");
            entry.put("title", d.getTitle());
            entry.put("date", d.getUploadedAt());
            recentActivity.add(entry);
        }

        List<Expense> recentExpenses = expenseRepository
                .findByUserIdOrderByDateDesc(userId)
                .stream().limit(5).collect(Collectors.toList());
        for (Expense e : recentExpenses) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("type", "expense");
            entry.put("title", e.getTitle());
            entry.put("date", e.getDate());
            recentActivity.add(entry);
        }

        recentActivity.sort((a, b) -> {
            Comparable<?> da = (Comparable<?>) a.get("date");
            Comparable<?> db = (Comparable<?>) b.get("date");
            return compareObjects(db, da);
        });
        if (recentActivity.size() > 10) {
            recentActivity = recentActivity.subList(0, 10);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalDocuments", totalDocuments);
        result.put("totalExpenses", totalExpenses);
        result.put("recentActivity", recentActivity);
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareObjects(Object a, Object b) {
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable) a).compareTo(b);
        }
        return 0;
    }
}
