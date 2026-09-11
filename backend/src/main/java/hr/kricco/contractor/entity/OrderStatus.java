package hr.kricco.contractor.entity;

// Which status can follow which is in the status_transitions table
public enum OrderStatus {
    DRAFT,
    PENDING,
    IN_PROGRESS,
    RESOLVED,
    CANCELLED
}
