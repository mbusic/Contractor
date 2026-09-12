package hr.kricco.contractor.service;

import hr.kricco.contractor.dto.OrderDto;
import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.ClientType;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.entity.Order;
import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.exception.ConflictException;
import hr.kricco.contractor.repository.ClientRepository;
import hr.kricco.contractor.repository.OrderRepository;
import hr.kricco.contractor.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

// Two servicers accept the same order at the same time. Not @Transactional: the two accepts need their
// own transactions and real commits, so the rows are committed and removed again in cleanUp.
//
// The order of events is forced, so the test always takes the @Version path:
// 1. The first accept flushes its UPDATE, but doesn't commit yet. Its row lock stays.
// 2. The second accept still reads the committed row (PENDING, unassigned), passes the check,
//    and its UPDATE waits for the row lock.
// 3. The first commits. The second UPDATE finds a newer version, changes 0 rows, and fails.
@SpringBootTest
class OrderAcceptRaceTest {

    private static final int TIMEOUT_SECONDS = 10;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE branches, clients, locations, users, order_sequences, orders, order_notes RESTART IDENTITY CASCADE");
    }

    @Test
    void secondServicerGetsConflictWhenBothAcceptAtOnce() throws Exception {
        Long orderId = savePendingOrder().getId();
        User first = saveServicer("servicer1");
        User second = saveServicer("servicer2");
        CountDownLatch firstFlushed = new CountDownLatch(1);
        CountDownLatch firstMayCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstAccept = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                orderService.acceptOrder(orderId, first);
                firstFlushed.countDown();
                await(firstMayCommit);
            }));
            await(firstFlushed);

            Future<OrderDto> secondAccept = executor.submit(() -> orderService.acceptOrder(orderId, second));
            waitUntilAnUpdateWaitsForTheRowLock();
            firstMayCommit.countDown();

            firstAccept.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThatThrownBy(() -> secondAccept.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Order is already taken or not pending");
        } finally {
            firstMayCommit.countDown();
            executor.shutdownNow();
        }

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(order.getAssignedServicer().getId()).isEqualTo(first.getId());
        assertThat(order.getVersion()).isEqualTo(1);
    }

    // Postgres shows a session that waits for a row lock with wait_event_type 'Lock'
    private void waitUntilAnUpdateWaitsForTheRowLock() throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (countSessionsWaitingForLock() == 0) {
            if (System.currentTimeMillis() > deadline) {
                fail("The second accept never waited for the row lock");
            }
            Thread.sleep(10);
        }
    }

    private int countSessionsWaitingForLock() {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND wait_event_type = 'Lock'
                """, Integer.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the other thread");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Order savePendingOrder() {
        Client client = new Client();
        client.setType(ClientType.COMPANY);
        client.setName("Petar Perić d.o.o.");
        Location location = new Location();
        location.setClient(client);
        location.setAddress("A.G. Matoša 42");
        location.setCity("Zagreb 10000");
        client.getLocations().add(location);
        clientRepository.save(client);

        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        order.setClient(client);
        order.setLocation(location);
        order.setOrderNumber("001/26");
        return orderRepository.save(order);
    }

    private User saveServicer(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("not-a-real-hash");
        user.setRole(Role.SERVICER);
        user.setDisplayName("Test " + username);
        return userRepository.save(user);
    }
}
