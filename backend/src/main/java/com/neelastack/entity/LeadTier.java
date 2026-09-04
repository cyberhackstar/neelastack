package com.neelastack.entity;

/**
 * Lead-scoring tier, per master prompt section 49 (80-100 HOT / 60-79 WARM / below WARM).
 */
public enum LeadTier {
    HOT,
    WARM,
    NURTURE
}
