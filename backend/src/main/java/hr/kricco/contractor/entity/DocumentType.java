package hr.kricco.contractor.entity;

// Printable documents made from an order. Built as HTML on every request, never stored (domain-model DocumentType).
public enum DocumentType {
    QUOTE,
    WORK_ORDER,
    REPORT,
    INVOICE
}
