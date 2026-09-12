package hr.kricco.contractor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// A photo on an order. Only added and deleted, so no version.
// The URL isn't stored: it's always /api/files/ + filename (domain-model C4).
@Entity
@Table(name = "order_photos")
@Getter
@Setter
@NoArgsConstructor
public class OrderPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // <uuid>.<ext> in app.upload-dir. The extension comes from the detected image type.
    @Column(nullable = false, unique = true)
    private String filename;
}
