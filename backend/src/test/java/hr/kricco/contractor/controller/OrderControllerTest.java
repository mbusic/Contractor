package hr.kricco.contractor.controller;

import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.ClientType;
import hr.kricco.contractor.entity.Costs;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.entity.Order;
import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.repository.BranchRepository;
import hr.kricco.contractor.repository.ClientRepository;
import hr.kricco.contractor.repository.OrderRepository;
import hr.kricco.contractor.repository.UserRepository;
import hr.kricco.contractor.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against contractor_test. Each test is rolled back, so tests don't see each other's rows.
// Requests run as real users (as(...)), because the service checks rows against the logged-in user.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerTest {

    private static final String ORDER_NUMBER_PATTERN = "\\d{3}/\\d{2}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    private Branch zagreb;
    private Client company;
    private Location companySite;
    private Location otherClientSite;
    private User admin;
    private User office;
    private User servicer;
    private User otherServicer;

    @BeforeEach
    void setUp() {
        zagreb = new Branch();
        zagreb.setName("Kricco Zagreb");
        branchRepository.save(zagreb);

        company = saveClient("Petar Perić d.o.o.", "A.G. Matoša 42");
        companySite = company.getLocations().getFirst();
        otherClientSite = saveClient("Ana Anić", "Flanatička 14").getLocations().getFirst();

        admin = saveUser("admin", Role.ADMIN);
        office = saveUser("office", Role.OFFICE);
        servicer = saveUser("servicer", Role.SERVICER);
        otherServicer = saveUser("servicer2", Role.SERVICER);
    }

    // List

    @Test
    void officeSeesAllOrdersNewestFirst() throws Exception {
        saveOrder(OrderStatus.DRAFT, null);
        saveOrder(OrderStatus.PENDING, null);
        Order newest = saveOrder(OrderStatus.IN_PROGRESS, servicer);

        mockMvc.perform(get("/api/orders").with(as(office)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(newest.getId()))
                .andExpect(jsonPath("$[0].clientName").value("Petar Perić d.o.o."))
                .andExpect(jsonPath("$[0].locationText").value("A.G. Matoša 42, Zagreb 10000"))
                .andExpect(jsonPath("$[0].assignedServicerName").value("Test servicer"));
    }

    @Test
    void servicerSeesOwnOrdersAndUnassignedPendingOnly() throws Exception {
        Order own = saveOrder(OrderStatus.IN_PROGRESS, servicer);
        Order unassignedPending = saveOrder(OrderStatus.PENDING, null);
        saveOrder(OrderStatus.IN_PROGRESS, otherServicer);
        saveOrder(OrderStatus.DRAFT, null);

        mockMvc.perform(get("/api/orders").with(as(servicer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(unassignedPending.getId()))
                .andExpect(jsonPath("$[1].id").value(own.getId()));
    }

    // Detail

    @Test
    void getOrderReturnsDetailWithAllowedStatusesAndTotalHours() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);
        Costs costs = new Costs();
        costs.setWorkHours(new BigDecimal("2.50"));
        costs.setNumberOfWorkers(3);
        order.setEstimatedCosts(costs);

        mockMvc.perform(get("/api/orders/{id}", order.getId()).with(as(office)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.allowedNextStatuses").value(contains("DRAFT", "RESOLVED", "CANCELLED")))
                .andExpect(jsonPath("$.client.name").value("Petar Perić d.o.o."))
                .andExpect(jsonPath("$.location.address").value("A.G. Matoša 42"))
                .andExpect(jsonPath("$.estimatedCosts.totalHours").value(7.5))
                .andExpect(jsonPath("$.estimatedCosts.km").value(nullValue()));
    }

    @Test
    void assignedOrderOffersInProgress() throws Exception {
        Order order = saveOrder(OrderStatus.RESOLVED, servicer);

        mockMvc.perform(get("/api/orders/{id}", order.getId()).with(as(office)))
                .andExpect(jsonPath("$.allowedNextStatuses").value(contains("DRAFT", "PENDING", "IN_PROGRESS", "CANCELLED")));
    }

    @Test
    void getUnknownOrderReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", 999999).with(as(office)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Order not found"));
    }

    @Test
    void servicerCannotSeeOrderOfAnotherServicer() throws Exception {
        Order order = saveOrder(OrderStatus.IN_PROGRESS, otherServicer);

        mockMvc.perform(get("/api/orders/{id}", order.getId()).with(as(servicer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can't see this order"));
    }

    @Test
    void servicerSeesUnassignedPendingOrderWithoutStatusChoices() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);

        mockMvc.perform(get("/api/orders/{id}", order.getId()).with(as(servicer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedNextStatuses.length()").value(0));
    }

    // Create

    @Test
    void createReturnsDraftWithoutNumber() throws Exception {
        mockMvc.perform(post("/api/orders").with(as(office))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(company.getId(), companySite.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.orderNumber").value(nullValue()))
                .andExpect(jsonPath("$.urgency").value("ONE_WEEK"))
                .andExpect(jsonPath("$.branch.name").value("Kricco Zagreb"))
                .andExpect(jsonPath("$.estimatedCosts.workHours").value(8))
                .andExpect(jsonPath("$.estimatedCosts.totalHours").value(16))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()));
    }

    @Test
    void createEmptyDraftIsAllowed() throws Exception {
        mockMvc.perform(post("/api/orders").with(as(office))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client").value(nullValue()));
    }

    @Test
    void createWithLocationOfAnotherClientReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders").with(as(office))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(company.getId(), otherClientSite.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Location doesn't belong to the client"));
    }

    @Test
    void createWithUnknownClientReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders").with(as(office))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(999999L, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Client not found"));
    }

    @Test
    void createWithNegativeKmReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders").with(as(office))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estimatedCosts": {"km": -5}}
                                """))
                .andExpect(status().isBadRequest());
    }

    // Update

    @Test
    void updateReplacesAllFields() throws Exception {
        Order order = saveOrder(OrderStatus.DRAFT, null);
        order.setDescription("Old description");

        mockMvc.perform(put("/api/orders/{id}", order.getId()).with(as(office))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactPerson": "Marko", "version": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactPerson").value("Marko"))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.client").value(nullValue()))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updateSubmittedOrderWithoutClientReturnsBadRequest() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);

        mockMvc.perform(put("/api/orders/{id}", order.getId()).with(as(office))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Client is required"));
    }

    @Test
    void updateWithStaleVersionReturnsConflict() throws Exception {
        Order order = saveOrder(OrderStatus.DRAFT, null);
        order.setDescription("Saved by someone else");
        orderRepository.saveAndFlush(order);

        mockMvc.perform(put("/api/orders/{id}", order.getId()).with(as(office))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactPerson": "Marko", "version": 0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Changed by someone else. Reload and try again."));
    }

    // Delete

    @Test
    void deleteRemovesOrder() throws Exception {
        Order order = saveOrder(OrderStatus.RESOLVED, servicer);

        mockMvc.perform(delete("/api/orders/{id}", order.getId()).with(as(admin)))
                .andExpect(status().isNoContent());

        assertThat(orderRepository.findById(order.getId())).isEmpty();
    }

    // Status changes

    @Test
    void submitDraftGivesItANumber() throws Exception {
        Order draft = saveDraft(company, companySite);

        changeStatus(draft, "PENDING", office)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderNumber").value(matchesPattern(ORDER_NUMBER_PATTERN)));
    }

    @Test
    void submittedDraftsGetConsecutiveNumbers() throws Exception {
        Order first = saveDraft(company, companySite);
        Order second = saveDraft(company, companySite);

        String firstNumber = numberAfterSubmit(first);
        String secondNumber = numberAfterSubmit(second);

        int firstSequence = Integer.parseInt(firstNumber.substring(0, 3));
        assertThat(secondNumber).isEqualTo("%03d%s".formatted(firstSequence + 1, firstNumber.substring(3)));
    }

    @Test
    void submitDraftWithoutLocationReturnsBadRequest() throws Exception {
        Order draft = saveDraft(company, null);

        changeStatus(draft, "PENDING", office)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Location is required"));

        assertThat(draft.getStatus()).isEqualTo(OrderStatus.DRAFT);
    }

    @Test
    void cancelDraftTakesNoNumber() throws Exception {
        Order draft = saveDraft(null, null);

        changeStatus(draft, "CANCELLED", office)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.orderNumber").value(nullValue()));
    }

    @Test
    void resubmittedOrderKeepsItsNumber() throws Exception {
        Order draft = saveDraft(company, companySite);
        String number = numberAfterSubmit(draft);

        changeStatus(draft, "DRAFT", office).andExpect(status().isOk());
        changeStatus(draft, "PENDING", office)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(number));
    }

    @Test
    void inProgressWithoutServicerReturnsConflict() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);

        changeStatus(order, "IN_PROGRESS", office)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Assign a servicer first"));
    }

    @Test
    void changeToSameStatusReturnsConflict() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);

        changeStatus(order, "PENDING", office)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Status change from PENDING to PENDING is not allowed"));
    }

    @Test
    void changeStatusWithStaleVersionReturnsConflict() throws Exception {
        Order draft = saveDraft(null, null);
        draft.setDescription("Saved by someone else");
        orderRepository.saveAndFlush(draft);

        mockMvc.perform(patch("/api/orders/{id}/status", draft.getId()).with(as(office))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELLED", "version": 0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Changed by someone else. Reload and try again."));
    }

    @Test
    void changeStatusWithoutVersionReturnsBadRequest() throws Exception {
        Order draft = saveDraft(null, null);

        mockMvc.perform(patch("/api/orders/{id}/status", draft.getId()).with(as(office))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELLED"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void servicerCanChangeOwnOrder() throws Exception {
        Order order = saveOrder(OrderStatus.IN_PROGRESS, servicer);

        changeStatus(order, "RESOLVED", servicer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void servicerCannotChangeUnassignedOrder() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);

        changeStatus(order, "CANCELLED", servicer)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can't change this order"));
    }

    @Test
    void movingInProgressOrderBackToPendingClearsServicer() throws Exception {
        Order order = saveOrder(OrderStatus.IN_PROGRESS, servicer);

        changeStatus(order, "PENDING", office)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.assignedServicer").value(nullValue()));
    }

    // Accept

    @Test
    void servicerAcceptsUnassignedPendingOrder() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);

        mockMvc.perform(post("/api/orders/{id}/accept", order.getId()).with(as(servicer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedServicer.id").value(servicer.getId()))
                .andExpect(jsonPath("$.assignedServicer.displayName").value("Test servicer"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void acceptOrderOfAnotherServicerReturnsConflict() throws Exception {
        Order order = saveOrder(OrderStatus.IN_PROGRESS, otherServicer);

        mockMvc.perform(post("/api/orders/{id}/accept", order.getId()).with(as(servicer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Order is already taken or not pending"));
    }

    @Test
    void acceptDraftReturnsConflict() throws Exception {
        Order draft = saveDraft(company, companySite);

        mockMvc.perform(post("/api/orders/{id}/accept", draft.getId()).with(as(servicer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Order is already taken or not pending"));
    }

    @Test
    void officeCannotAcceptOrder() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);

        mockMvc.perform(post("/api/orders/{id}/accept", order.getId()).with(as(office)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied"));
    }

    // Assignment

    @Test
    void assignPendingOrderMovesItToInProgress() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);

        assign(order, servicer.getId(), office)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedServicer.id").value(servicer.getId()))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void reassignInProgressOrderKeepsStatus() throws Exception {
        Order order = saveOrder(OrderStatus.IN_PROGRESS, servicer);

        assign(order, otherServicer.getId(), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedServicer.id").value(otherServicer.getId()));
    }

    @Test
    void assignResolvedOrderReturnsConflict() throws Exception {
        Order order = saveOrder(OrderStatus.RESOLVED, servicer);

        assign(order, otherServicer.getId(), office)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only PENDING and IN_PROGRESS orders can be assigned"));
    }

    @Test
    void assignNonServicerReturnsBadRequest() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);

        assign(order, office.getId(), office)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("User is not a servicer"));
    }

    @Test
    void assignDeactivatedServicerReturnsBadRequest() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);
        otherServicer.setActive(false);

        assign(order, otherServicer.getId(), office)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Servicer is not active"));
    }

    @Test
    void assignUnknownServicerReturnsBadRequest() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);

        assign(order, 999999L, office)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Servicer not found"));
    }

    @Test
    void assignWithStaleVersionReturnsConflict() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, null);
        order.setDescription("Saved by someone else");
        orderRepository.saveAndFlush(order);

        mockMvc.perform(put("/api/orders/{id}/assignment", order.getId()).with(as(office))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"servicerId": %d, "version": 0}
                                """.formatted(servicer.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Changed by someone else. Reload and try again."));
    }

    // Actual costs

    @Test
    void updateActualCostsReplacesThemAndShowsDifference() throws Exception {
        Order order = saveOrder(OrderStatus.RESOLVED, servicer);
        Costs estimated = new Costs();
        estimated.setKm(80);
        estimated.setWorkHours(new BigDecimal("8.00"));
        estimated.setNumberOfWorkers(2);
        estimated.setMaterialCost(new BigDecimal("50.00"));
        order.setEstimatedCosts(estimated);

        updateActualCosts(order, """
                {"km": 95, "workHours": 7.5, "numberOfWorkers": 3, "materialCost": 42.50}
                """, office)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualCosts.km").value(95))
                .andExpect(jsonPath("$.actualCosts.totalHours").value(22.5))
                .andExpect(jsonPath("$.costDifference.km").value(15))
                .andExpect(jsonPath("$.costDifference.workHours").value(-0.5))
                .andExpect(jsonPath("$.costDifference.numberOfWorkers").value(1))
                .andExpect(jsonPath("$.costDifference.totalHours").value(6.5))
                .andExpect(jsonPath("$.costDifference.materialCost").value(-7.5))
                .andExpect(jsonPath("$.estimatedCosts.km").value(80))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void costDifferenceIsEmptyWhereAValueIsMissing() throws Exception {
        Order order = saveOrder(OrderStatus.IN_PROGRESS, servicer);
        Costs estimated = new Costs();
        estimated.setKm(80);
        order.setEstimatedCosts(estimated);

        updateActualCosts(order, """
                {"km": 70, "workHours": 4}
                """, servicer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costDifference.km").value(-10))
                .andExpect(jsonPath("$.costDifference.workHours").value(nullValue()))
                .andExpect(jsonPath("$.costDifference.totalHours").value(nullValue()));
    }

    @Test
    void emptyActualCostsClearAllFields() throws Exception {
        Order order = saveOrder(OrderStatus.IN_PROGRESS, servicer);
        Costs actual = new Costs();
        actual.setKm(40);
        order.setActualCosts(actual);

        updateActualCosts(order, "{}", servicer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualCosts.km").value(nullValue()));
    }

    @Test
    void servicerCannotUpdateActualCostsOfAnotherServicersOrder() throws Exception {
        Order order = saveOrder(OrderStatus.IN_PROGRESS, otherServicer);

        updateActualCosts(order, """
                {"km": 95}
                """, servicer)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can't change this order"));
    }

    @Test
    void negativeActualCostReturnsBadRequest() throws Exception {
        Order order = saveOrder(OrderStatus.IN_PROGRESS, servicer);

        updateActualCosts(order, """
                {"km": -5}
                """, servicer)
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateActualCostsWithoutCostsReturnsBadRequest() throws Exception {
        Order order = saveOrder(OrderStatus.IN_PROGRESS, servicer);

        mockMvc.perform(put("/api/orders/{id}/actual-costs", order.getId()).with(as(servicer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateActualCostsWithStaleVersionReturnsConflict() throws Exception {
        Order order = saveOrder(OrderStatus.IN_PROGRESS, servicer);
        order.setDescription("Saved by someone else");
        orderRepository.saveAndFlush(order);

        mockMvc.perform(put("/api/orders/{id}/actual-costs", order.getId()).with(as(servicer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"costs": {"km": 95}, "version": 0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Changed by someone else. Reload and try again."));
    }

    // Access by role

    // Valid bodies on purpose: the body is validated before @PreAuthorize runs, so a bad body would give 400
    static Stream<MockHttpServletRequestBuilder> allEndpoints() {
        return Stream.of(
                get("/api/orders"),
                get("/api/orders/1"),
                post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/orders/1").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/orders/1"),
                patch("/api/orders/1/status").contentType(MediaType.APPLICATION_JSON).content("""
                        {"status": "CANCELLED", "version": 0}
                        """),
                post("/api/orders/1/accept"),
                put("/api/orders/1/assignment").contentType(MediaType.APPLICATION_JSON).content("""
                        {"servicerId": 1, "version": 0}
                        """),
                put("/api/orders/1/actual-costs").contentType(MediaType.APPLICATION_JSON).content("""
                        {"costs": {}, "version": 0}
                        """));
    }

    @ParameterizedTest
    @MethodSource("allEndpoints")
    @WithMockUser(roles = "CLIENT")
    void clientUserCannotCallOrderEndpoints(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden());
    }

    static Stream<MockHttpServletRequestBuilder> officeOnlyEndpoints() {
        return Stream.of(
                post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/orders/1").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/orders/1"),
                put("/api/orders/1/assignment").contentType(MediaType.APPLICATION_JSON).content("""
                        {"servicerId": 1, "version": 0}
                        """));
    }

    @ParameterizedTest
    @MethodSource("officeOnlyEndpoints")
    @WithMockUser(roles = "SERVICER")
    void servicerCannotCallOfficeOnlyEndpoints(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden());
    }

    // Sends the order's current version. The test and the request share one transaction,
    // so the service changes this same instance and its version stays up to date.
    private ResultActions changeStatus(Order order, String status, User user) throws Exception {
        return mockMvc.perform(patch("/api/orders/{id}/status", order.getId()).with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s", "version": %d}
                        """.formatted(status, order.getVersion())));
    }

    // Wraps the costs JSON and sends the order's current version, see changeStatus
    private ResultActions updateActualCosts(Order order, String costsJson, User user) throws Exception {
        return mockMvc.perform(put("/api/orders/{id}/actual-costs", order.getId()).with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"costs": %s, "version": %d}
                        """.formatted(costsJson.strip(), order.getVersion())));
    }

    // Sends the order's current version, see changeStatus
    private ResultActions assign(Order order, Long servicerId, User user) throws Exception {
        return mockMvc.perform(put("/api/orders/{id}/assignment", order.getId()).with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"servicerId": %d, "version": %d}
                        """.formatted(servicerId, order.getVersion())));
    }

    private String numberAfterSubmit(Order draft) throws Exception {
        String body = changeStatus(draft, "PENDING", office)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return read(body, "$.orderNumber");
    }

    private String orderJson(Long clientId, Long locationId) {
        return """
                {"branchId": %d, "clientId": %s, "locationId": %s, "contactPerson": "Petar Perić",
                 "description": "Kvar na instalaciji", "urgency": "ONE_WEEK",
                 "estimatedCosts": {"km": 80, "workHours": 8, "numberOfWorkers": 2, "materialCost": 50.00}}
                """.formatted(zagreb.getId(), clientId, locationId);
    }

    private static RequestPostProcessor as(User user) {
        return user(new UserPrincipal(user));
    }

    // A submitted order: client, location and a unique number, like OrderService would leave it
    private Order saveOrder(OrderStatus status, User assignedServicer) {
        Order order = new Order();
        order.setStatus(status);
        order.setClient(company);
        order.setLocation(companySite);
        order.setAssignedServicer(assignedServicer);
        if (status != OrderStatus.DRAFT) {
            order.setOrderNumber("T" + System.nanoTime());
        }
        return orderRepository.save(order);
    }

    private Order saveDraft(Client client, Location location) {
        Order order = new Order();
        order.setStatus(OrderStatus.DRAFT);
        order.setClient(client);
        order.setLocation(location);
        return orderRepository.save(order);
    }

    private Client saveClient(String name, String address) {
        Client client = new Client();
        client.setType(ClientType.COMPANY);
        client.setName(name);
        Location location = new Location();
        location.setClient(client);
        location.setAddress(address);
        location.setCity("Zagreb 10000");
        client.getLocations().add(location);
        return clientRepository.save(client);
    }

    private User saveUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("not-a-real-hash");
        user.setRole(role);
        user.setDisplayName("Test " + username);
        if (role == Role.OFFICE || role == Role.SERVICER) {
            user.setBranch(zagreb);
        }
        return userRepository.save(user);
    }
}
