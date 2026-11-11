package com.riwi.skillbridge.domain.model;

/**
 * Represents the type of input used to trigger an AI recommendation.
 * TEXT: plain text goal entered by the user.
 * VOICE: voice transcription (future capability).
 * IMAGE: image-based input (future capability).
 */
public enum RecommendationInputType {
    TEXT,
    VOICE,
    IMAGE
}
