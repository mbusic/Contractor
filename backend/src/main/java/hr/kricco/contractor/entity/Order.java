package hr.kricco.contractor.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

// One repair or maintenance job. The table is "orders" because "order" is a reserved word in SQL.
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NNN/YY, e.g. 007/26. Taken the first time the order is submitted (PENDING, IN_PROGRESS or RESOLVED),
    // so a draft or a cancelled draft has none (domain-model Q4).
    @Column(unique = true)
    private String orderNumber;

    // Optional, set by the office (domain-model Q1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    // Required outside DRAFT and CANCELLED
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    // Required outside DRAFT and CANCELLED. Always belongs to the order's client.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    // Contact on site for this order, can differ from the client's contact
    private String contactPerson;

    private String phone;

    private String email;

    // What needs to be fixed
    private String description;

    @Enumerated(EnumType.STRING)
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // Always a SERVICER. One servicer account can stand for a whole crew.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_servicer_id")
    private User assignedServicer;

    // Used for the quote
    @Embedded
    @AttributeOverride(name = "km", column = @Column(name = "estimated_km"))
    @AttributeOverride(name = "workHours", column = @Column(name = "estimated_work_hours"))
    @AttributeOverride(name = "numberOfWorkers", column = @Column(name = "estimated_number_of_workers"))
    @AttributeOverride(name = "materialCost", column = @Column(name = "estimated_material_cost"))
    private Costs estimatedCosts;

    // Used for the report and the invoice. Entered by hand until the time sheet exists.
    @Embedded
    @AttributeOverride(name = "km", column = @Column(name = "actual_km"))
    @AttributeOverride(name = "workHours", column = @Column(name = "actual_work_hours"))
    @AttributeOverride(name = "numberOfWorkers", column = @Column(name = "actual_number_of_workers"))
    @AttributeOverride(name = "materialCost", column = @Column(name = "actual_material_cost"))
    private Costs actualCosts;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
