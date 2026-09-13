package hr.kricco.contractor;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Every test context starts from an empty contractor_test with all migrations applied,
// so the test database always matches db/migration. Picked up by the component scan of the app.
@Configuration
public class CleanDatabaseBeforeTests {

    @Bean
    public FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
