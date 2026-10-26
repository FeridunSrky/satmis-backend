package com.satmis.common;

// Lets one request DTO serve both create and update, with update-only constraints in OnUpdate
public final class ValidationGroups {

    public interface OnUpdate {
    }

    private ValidationGroups() {
    }
}
