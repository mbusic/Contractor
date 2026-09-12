package hr.kricco.contractor.controller;

import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.ClientType;
import hr.kricco.contractor.entity.Costs;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.entity.Order;
import hr.kricco.contractor.entity.OrderNote;
import hr.kricco.contractor.entity.OrderPhoto;
import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.Urgency;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.repository.BranchRepository;
import hr.kricco.contractor.repository.ClientRepository;
import hr.kricco.contractor.repository.OrderRepository;
import hr.kricco.contractor.repository.UserRepository;
import hr.kricco.contractor.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The printable documents: GET /api/orders/{id}/documents/{type} and GET /api/portal/orders/{id}/documents/{type}.
// Runs against contractor_test, each test is rolled back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentEndpointsTest {

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

    private Client company;
    private User office;
    private User servicer;
    private User otherServicer;
    private User clientUser;
    private Order order;

    @BeforeEach
    void setUp() {
        Branch zagreb = new Branch();
        zagreb.setName("Kricco Zagreb");
        branchRepository.save(zagreb);
        company = saveClient("Petar Perić d.o.o.");
        office = saveUser("office", Role.OFFICE, null);
        servicer = saveUser("servicer", Role.SERVICER, null);
        otherServicer = saveUser("servicer2", Role.SERVICER, null);
        clientUser = saveUser("client", Role.CLIENT, company);
        order = saveOrder(company, zagreb, servicer);
    }

    // Employees

    @ParameterizedTest
    @CsvSource({
            "QUOTE,      PONUDA",
            "WORK_ORDER, RADNI NALOG",
            "REPORT,     IZVJEŠTAJ O RADOVIMA",
            "INVOICE,    RAČUN"
    })
    void officeGetsEveryDocumentAsHtml(String type, String title) throws Exception {
        document(order, type, office)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<h1>" + title + "</h1>")))
                .andExpect(content().string(containsString("001/26")))
                .andExpect(content().string(containsString("Kricco Zagreb")))
                .andExpect(content().string(containsString("Ispis / PDF")));
    }

    // The frontend opens the page with the app's origin, so a script in the data must never run
    @Test
    void valuesFromTheDatabaseAreEscaped() throws Exception {
        document(order, "REPORT", office)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))))
                .andExpect(content().string(containsString("&lt;b&gt;Oprez&lt;/b&gt;")));
    }

    @Test
    void quoteShowsEstimatedCostsAndLabels() throws Exception {
        document(order, "QUOTE", office)
                .andExpect(content().string(containsString("<tr><th>Ukupno sati</th><td>16.00</td></tr>")))
                .andExpect(content().string(containsString("<td>80 km</td>")))
                .andExpect(content().string(containsString("1 tjedan")))
                .andExpect(content().string(containsString("Skladište – A.G. Matoša 42, Zagreb 10000")));
    }

    @Test
    void reportShowsPhotosWithAbsoluteUrl() throws Exception {
        document(order, "REPORT", office)
                .andExpect(content().string(containsString(
                        "<img src=\"http://localhost:8080/api/files/00000000-0000-0000-0000-000000000000.jpg\"")));
    }

    @Test
    void draftDocumentShowsNacrtInsteadOfNumber() throws Exception {
        Order draft = new Order();
        draft.setStatus(OrderStatus.DRAFT);
        orderRepository.save(draft);

        document(draft, "QUOTE", office)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>PONUDA – Nacrt</title>")));
    }

    @Test
    void servicerGetsWorkOrderOfOwnOrder() throws Exception {
        document(order, "WORK_ORDER", servicer)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RADNI NALOG")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUOTE", "REPORT", "INVOICE"})
    void servicerCannotOpenOtherDocuments(String type) throws Exception {
        document(order, type, servicer)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can't open this document"));
    }

    @Test
    void servicerCannotOpenWorkOrderOfAnotherServicersOrder() throws Exception {
        document(order, "WORK_ORDER", otherServicer)
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownTypeReturnsBadRequest() throws Exception {
        document(order, "RECEIPT", office)
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrderReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/{id}/documents/QUOTE", 999999).with(as(office)))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientCannotUseEmployeeDocumentEndpoint() throws Exception {
        document(order, "QUOTE", clientUser)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied"));
    }

    // Portal

    @ParameterizedTest
    @ValueSource(strings = {"QUOTE", "REPORT", "INVOICE"})
    void clientGetsDocumentsOfOwnOrder(String type) throws Exception {
        portalDocument(order, type, clientUser)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("001/26")));
    }

    @Test
    void clientCannotOpenWorkOrder() throws Exception {
        portalDocument(order, "WORK_ORDER", clientUser)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can't open this document"));
    }

    @Test
    void clientCannotOpenDocumentOfAnotherClientsOrder() throws Exception {
        Order otherClientsOrder = saveOrder(saveClient("Ana Anić"), null, null);

        portalDocument(otherClientsOrder, "QUOTE", clientUser)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can't see this order"));
    }

    @Test
    void officeCannotUsePortalDocumentEndpoint() throws Exception {
        portalDocument(order, "QUOTE", office)
                .andExpect(status().isForbidden());
    }

    private ResultActions document(Order target, String type, User user) throws Exception {
        return mockMvc.perform(get("/api/orders/{id}/documents/{type}", target.getId(), type).with(as(user)));
    }

    private ResultActions portalDocument(Order target, String type, User user) throws Exception {
        return mockMvc.perform(get("/api/portal/orders/{id}/documents/{type}", target.getId(), type).with(as(user)));
    }

    private static RequestPostProcessor as(User user) {
        return user(new UserPrincipal(user));
    }

    // A submitted order with every part a document shows, and HTML in the texts to check the escaping
    private Order saveOrder(Client client, Branch branch, User assignedServicer) {
        Order newOrder = new Order();
        newOrder.setStatus(OrderStatus.IN_PROGRESS);
        newOrder.setOrderNumber(client == company ? "001/26" : "002/26");
        newOrder.setClient(client);
        newOrder.setLocation(client.getLocations().getFirst());
        newOrder.setBranch(branch);
        newOrder.setAssignedServicer(assignedServicer);
        newOrder.setUrgency(Urgency.ONE_WEEK);
        newOrder.setDescription("<script>alert(1)</script>");
        Costs estimated = new Costs();
        estimated.setKm(80);
        estimated.setWorkHours(new BigDecimal("8.00"));
        estimated.setNumberOfWorkers(2);
        newOrder.setEstimatedCosts(estimated);
        orderRepository.save(newOrder);

        OrderNote note = new OrderNote();
        note.setOrder(newOrder);
        note.setAuthor(office);
        note.setText("<b>Oprez</b>");
        newOrder.getNotes().add(note);
        OrderPhoto photo = new OrderPhoto();
        photo.setOrder(newOrder);
        photo.setFilename(client == company ? "00000000-0000-0000-0000-000000000000.jpg" : "11111111-1111-1111-1111-111111111111.jpg");
        newOrder.getPhotos().add(photo);
        return orderRepository.saveAndFlush(newOrder);
    }

    private Client saveClient(String name) {
        Client client = new Client();
        client.setType(ClientType.COMPANY);
        client.setName(name);
        Location location = new Location();
        location.setClient(client);
        location.setName("Skladište");
        location.setAddress("A.G. Matoša 42");
        location.setCity("Zagreb 10000");
        client.getLocations().add(location);
        return clientRepository.save(client);
    }

    private User saveUser(String username, Role role, Client client) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("not-a-real-hash");
        user.setRole(role);
        user.setDisplayName("Test " + username);
        user.setClient(client);
        return userRepository.save(user);
    }
}
