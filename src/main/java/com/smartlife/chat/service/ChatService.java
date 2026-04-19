package com.smartlife.chat.service;

import com.smartlife.automation.repository.ReminderRepository;
import com.smartlife.chat.dto.ChatMessageDto;
import com.smartlife.chat.dto.ChatRequest;
import com.smartlife.chat.dto.ChatResponse;
import com.smartlife.config.OllamaService;
import com.smartlife.document.model.Document;
import com.smartlife.document.repository.DocumentRepository;
import com.smartlife.expense.model.Expense;
import com.smartlife.expense.repository.ExpenseRepository;
import com.smartlife.health.model.HealthLog;
import com.smartlife.health.repository.HealthLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG Chatbot Service - assembles user life context and sends to Llama 3.2.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final OllamaService ollamaService;
    private final ExpenseRepository expenseRepository;
    private final HealthLogRepository healthLogRepository;
    private final DocumentRepository documentRepository;
    private final ReminderRepository reminderRepository;

    private final Map<String, List<OllamaService.ChatMessage>> conversations = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessageDto>> historyDtos = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_TURNS = 10;

    public ChatResponse chat(ChatRequest request, UUID userId) {
        String convId = request.conversationId() != null ? request.conversationId() : UUID.randomUUID().toString();
        List<OllamaService.ChatMessage> messages = conversations.computeIfAbsent(convId, k -> new ArrayList<>());
        List<ChatMessageDto> dtoHistory = historyDtos.computeIfAbsent(convId, k -> new ArrayList<>());

        if (messages.isEmpty()) {
            messages.add(OllamaService.systemMessage(buildSystemPrompt(buildLifeContext(userId))));
        }

        messages.add(OllamaService.userMessage(request.message()));
        dtoHistory.add(new ChatMessageDto("user", request.message(), Instant.now()));
        trimHistory(messages);

        String reply = ollamaService.chat(messages);
        boolean aiPowered = reply != null;
        if (reply == null) reply = buildFallbackReply(request.message());

        messages.add(OllamaService.assistantMessage(reply));
        dtoHistory.add(new ChatMessageDto("assistant", reply, Instant.now()));

        return new ChatResponse(convId, reply, List.copyOf(dtoHistory), aiPowered);
    }

    public void clearConversation(String conversationId) {
        conversations.remove(conversationId);
        historyDtos.remove(conversationId);
    }

    private String buildLifeContext(UUID userId) {
        StringBuilder ctx = new StringBuilder();

        try {
            LocalDate from = LocalDate.now().minusDays(30);
            BigDecimal total = expenseRepository.getTotalByUserAndDateRange(userId, from, LocalDate.now());
            var cats = expenseRepository.getCategoryTotals(userId, from, LocalDate.now());
            List<Expense> anomalies = expenseRepository.findAnomalies(userId);
            ctx.append("=== EXPENSES (last 30 days) ===\n");
            ctx.append("Total: Rs.").append(total != null ? total : 0).append("\n");
            cats.stream().limit(6).forEach(ct ->
                ctx.append("  ").append(ct.getCategory().name()).append(": Rs.").append(ct.getTotal()).append("\n"));
            if (!anomalies.isEmpty()) ctx.append("Anomalies: ").append(anomalies.size()).append(" unusual\n");
        } catch (Exception e) {
            ctx.append("EXPENSES: unavailable\n");
        }

        try {
            LocalDate from = LocalDate.now().minusDays(14);
            List<HealthLog> logs = healthLogRepository.findByUserAndDateRange(userId, from, LocalDate.now());
            Double avgSleep = healthLogRepository.getAvgSleepHours(userId, from);
            Double avgMood = healthLogRepository.getAvgMoodScore(userId, from);
            Double avgWt = healthLogRepository.getAvgWeight(userId, from);
            ctx.append("\n=== HEALTH (last 14 days) ===\n");
            ctx.append("Entries: ").append(logs.size()).append("\n");
            if (avgSleep != null) ctx.append("Avg sleep: ").append(String.format("%.1f", avgSleep)).append("h\n");
            if (avgMood != null) ctx.append("Avg mood: ").append(String.format("%.1f", avgMood)).append("/10\n");
            if (avgWt != null) ctx.append("Avg weight: ").append(String.format("%.1f", avgWt)).append("kg\n");
            if (!logs.isEmpty()) {
                HealthLog latest = logs.get(logs.size() - 1);
                ctx.append("Latest (").append(latest.getLogDate()).append("):");
                if (latest.getSleepHours() != null) ctx.append(" sleep=").append(latest.getSleepHours()).append("h");
                if (latest.getMoodScore() != null) ctx.append(" mood=").append(latest.getMoodScore());
                if (latest.getStressLevel() != null) ctx.append(" stress=").append(latest.getStressLevel());
                if (latest.getExerciseMinutes() != null) ctx.append(" exercise=").append(latest.getExerciseMinutes()).append("min");
                ctx.append("\n");
            }
        } catch (Exception e) {
            ctx.append("HEALTH: unavailable\n");
        }

        try {
            long total = documentRepository.findByUserId(userId, PageRequest.of(0, 1)).getTotalElements();
            List<Document> expiring = documentRepository.findExpiringDocuments(
                userId, LocalDate.now(), LocalDate.now().plusDays(30));
            ctx.append("\n=== DOCUMENTS ===\n");
            ctx.append("Total: ").append(total).append("\n");
            expiring.forEach(d -> ctx.append("  Expiring: ")
                .append(d.getTitle() != null ? d.getTitle() : d.getFileName())
                .append(" on ").append(d.getExpiryDate()).append("\n"));
        } catch (Exception e) {
            ctx.append("DOCUMENTS: unavailable\n");
        }

        try {
            var reminders = reminderRepository.findByUserIdAndSentFalseOrderByScheduledAtAsc(userId);
            ctx.append("\n=== REMINDERS ===\n");
            if (reminders.isEmpty()) {
                ctx.append("None pending\n");
            } else {
                reminders.stream().limit(5).forEach(r ->
                    ctx.append("  - ").append(r.getTitle()).append(" at ").append(r.getScheduledAt()).append("\n"));
            }
        } catch (Exception e) {
            ctx.append("REMINDERS: unavailable\n");
        }

        return ctx.toString();
    }

    private String buildSystemPrompt(String context) {
        return "You are SmartLife Assistant, a personal AI life coach with access to the user real-time life data.\n"
            + "Provide personalised, actionable insights. Be concise and empathetic. Keep replies under 200 words unless asked for more.\n"
            + "\nUSER LIFE CONTEXT:\n" + context;
    }

    private void trimHistory(List<OllamaService.ChatMessage> messages) {
        while (messages.size() > MAX_HISTORY_TURNS * 2 + 1) {
            messages.remove(1);
            if (messages.size() > 1) messages.remove(1);
        }
    }

    private String buildFallbackReply(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("health") || lower.contains("sleep") || lower.contains("mood"))
            return "AI engine is temporarily offline. Check GET /api/v1/health/insights.";
        if (lower.contains("expense") || lower.contains("spend") || lower.contains("budget"))
            return "AI engine is offline. See GET /api/v1/expenses/summary/current.";
        if (lower.contains("document") || lower.contains("expir"))
            return "AI offline. Check GET /api/v1/documents/expiring.";
        return "I am having trouble connecting to the AI engine. Please try again shortly.";
    }
}
