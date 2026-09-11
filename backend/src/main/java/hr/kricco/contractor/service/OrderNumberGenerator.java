package hr.kricco.contractor.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.ZoneId;

// Gives out order numbers NNN/YY (e.g. 007/26). The counter starts again from 1 every year.
@Component
@RequiredArgsConstructor
public class OrderNumberGenerator {

    private static final ZoneId ZAGREB = ZoneId.of("Europe/Zagreb");

    // Creates the year's row or increases it, in one statement. Postgres locks the row until the
    // transaction ends, so two orders at the same moment can't get the same number, not even for the
    // first order of a new year.
    private static final String NEXT_SEQUENCE_SQL = """
            INSERT INTO order_sequences (seq_year, last_sequence) VALUES (:year, 1)
            ON CONFLICT (seq_year) DO UPDATE SET last_sequence = order_sequences.last_sequence + 1
            RETURNING last_sequence
            """;

    private final JdbcClient jdbcClient;

    // Must run in the transaction that saves the order: if that transaction rolls back, the number is given back
    @Transactional(propagation = Propagation.MANDATORY)
    public String next() {
        int year = Year.now(ZAGREB).getValue();
        int sequence = jdbcClient.sql(NEXT_SEQUENCE_SQL)
                .param("year", year)
                .query(Integer.class)
                .single();
        return "%03d/%02d".formatted(sequence, year % 100);
    }
}
