package com.univent.model.enums;

public enum VerificationStatus {
    UNVERIFIED,
    ID_PENDING,
    PENDING,     // ID uploaded, waiting for review
    VERIFIED,    // ID approved - verified badge shown
    REJECTED     // ID rejected
}