package com.neelastack.entity;

/** Input type for an admin-configured dynamic booking-form field. See master prompt section 7. */
public enum FormFieldType {
    TEXT,
    TEXTAREA,
    EMAIL,
    PHONE,
    NUMBER,
    SELECT,
    MULTI_SELECT,
    RADIO,
    CHECKBOX,
    URL,
    DATE
}
