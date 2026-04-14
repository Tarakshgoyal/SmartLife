package com.smartlife.document.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Elasticsearch document model for full-text document search.
 * Mirrors the PostgreSQL Document entity but is stored in Elasticsearch.
 */
@Document(indexName = "smartlife-documents", createIndex = false)
public record DocumentSearchDocument(
        @Id String id,
        @Field(type = FieldType.Keyword) String userId,
        @Field(type = FieldType.Text, analyzer = "standard") String title,
        @Field(type = FieldType.Text, analyzer = "standard") String extractedText,
        @Field(type = FieldType.Keyword) String documentType,
        @Field(type = FieldType.Text) String tags,
        @Field(type = FieldType.Text) String notes,
        @Field(type = FieldType.Date)  LocalDate expiryDate,
        @Field(type = FieldType.Date)  LocalDateTime uploadedAt
) {}
