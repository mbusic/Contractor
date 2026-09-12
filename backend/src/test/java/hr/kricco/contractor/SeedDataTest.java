package hr.kricco.contractor;

import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.ClientType;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.entity.Order;
import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.repository.BranchRepository;
import hr.kricco.contractor.repository.ClientRepository;
import hr.kricco.contractor.repository.OrderRepository;
import hr.kricco.contractor.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// Checks that seed.sql still runs against the current schema.sql.
// The script runs inside the test transaction, so everything is rolled back afterwards.
@SpringBootTest
@Transactional
@Sql("classpath:seed.sql")
class SeedDataTest {

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seedCreatesFiveBranches() {
        assertThat(branchRepository.findAll())
                .extracting(Branch::getCity)
                .containsExactlyInAnyOrder("Zagreb", "Split", "Zadar", "Osijek", "Pula");
    }

    @Test
    void seedStartsIdsFromOne() {
        Branch first = branchRepository.findById(1L).orElseThrow();

        assertThat(first.getName()).isEqualTo("Kricco Zagreb");
    }

    @Test
    void seedCreatesOneUserPerRole() {
        assertThat(userRepository.findAll())
                .extracting(User::getUsername, User::getRole)
                .containsExactlyInAnyOrder(
                        tuple("admin", Role.ADMIN),
                        tuple("office", Role.OFFICE),
                        tuple("servicer", Role.SERVICER),
                        tuple("client", Role.CLIENT));
    }

    @Test
    void seedPutsClientUserInTheCompany() {
        User clientUser = userRepository.findByUsername("client").orElseThrow();

        assertThat(clientUser.getClient().getName()).isEqualTo("Petar Perić d.o.o.");
        assertThat(clientUser.getBranch()).isNull();
    }

    @Test
    void seedPutsOfficeAndServicerInZagreb() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        User office = userRepository.findByUsername("office").orElseThrow();
        User servicer = userRepository.findByUsername("servicer").orElseThrow();

        assertThat(admin.getBranch()).isNull();
        assertThat(office.getBranch().getCity()).isEqualTo("Zagreb");
        assertThat(servicer.getBranch().getCity()).isEqualTo("Zagreb");
    }

    @Test
    void seedPasswordsMatchUsernames() {
        for (String username : List.of("admin", "office", "servicer", "client")) {
            User user = userRepository.findByUsername(username).orElseThrow();

            assertThat(passwordEncoder.matches(username, user.getPassword()))
                    .as("password of %s", username)
                    .isTrue();
        }
    }

    @Test
    void seedCreatesTwoClientsWithTheirLocations() {
        Client company = clientRepository.findById(1L).orElseThrow();
        Client individual = clientRepository.findById(2L).orElseThrow();

        assertThat(company.getType()).isEqualTo(ClientType.COMPANY);
        assertThat(company.getLocations()).extracting(Location::getCity)
                .containsExactly("Zagreb 10000", "Split 21000", "Osijek 31000");
        assertThat(individual.getType()).isEqualTo(ClientType.INDIVIDUAL);
        assertThat(individual.getLocations()).extracting(Location::getCity)
                .containsExactly("Zadar 23000", "Pula 52100");
    }

    // The template's five orders plus one draft a client user started in the portal
    @Test
    void seedCreatesSixOrdersAtTheirClientsLocations() {
        List<Order> orders = orderRepository.findAll();

        assertThat(orders).extracting(Order::getOrderNumber, Order::getStatus, Order::isCreatedInPortal)
                .containsExactlyInAnyOrder(
                        tuple("001/26", OrderStatus.RESOLVED, false),
                        tuple("002/26", OrderStatus.IN_PROGRESS, false),
                        tuple("003/26", OrderStatus.PENDING, false),
                        tuple("004/26", OrderStatus.PENDING, false),
                        tuple("005/26", OrderStatus.PENDING, false),
                        tuple(null, OrderStatus.DRAFT, true));
        for (Order order : orders) {
            assertThat(order.getLocation().getClient().getId())
                    .as("location of %s belongs to its client", order.getOrderNumber())
                    .isEqualTo(order.getClient().getId());
        }
    }

    @Test
    void seedOrderHasCalculatedTotalHours() {
        Order resolved = orderRepository.findAll().stream()
                .filter(order -> "001/26".equals(order.getOrderNumber()))
                .findFirst()
                .orElseThrow();

        assertThat(resolved.getActualCosts().getTotalHours()).isEqualByComparingTo("24");
        assertThat(resolved.getAssignedServicer().getUsername()).isEqualTo("servicer");
    }

    // Also checks @OrderBy on Order.notes: the notes are loaded from the database, newest first
    @Test
    void seedResolvedOrderHasNotesNewestFirst() {
        Order resolved = orderRepository.findAll().stream()
                .filter(order -> "001/26".equals(order.getOrderNumber()))
                .findFirst()
                .orElseThrow();

        assertThat(resolved.getNotes())
                .extracting(note -> note.getAuthor().getUsername())
                .containsExactly("office", "servicer");
    }

    // Catches a wrong file encoding when the script is read
    @Test
    void seedKeepsCroatianLetters() {
        User office = userRepository.findByUsername("office").orElseThrow();

        assertThat(office.getDisplayName()).isEqualTo("Dispečer");
    }
}
