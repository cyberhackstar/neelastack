package com.neelastack.entity;

public enum Role {
    /**
     * The highest-risk account operations (see {@link com.neelastack.service.MfaService#forceReset})
     * require this authority specifically, not just ADMIN. A SUPERADMIN also holds every ADMIN
     * permission (see {@link User#getAuthorities()}) -- it's a superset, not a separate track.
     */
    SUPERADMIN,
    ADMIN,
    CLIENT
}
