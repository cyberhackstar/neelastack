package com.neelastack.entity;

/**
 * Which of the three commercial entry points (master prompt section 17) a lead came
 * through. GENERAL covers the plain /contact form, which predates the estimator and
 * doesn't collect enough detail to say more than "reached out."
 */
public enum InquiryIntent {
    BUILD,
    FIX,
    MODERNIZE,
    AUDIT,
    GENERAL
}
