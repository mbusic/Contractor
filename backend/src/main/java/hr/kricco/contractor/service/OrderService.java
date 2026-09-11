package hr.kricco.contractor.service;

import hr.kricco.contractor.dto.BranchDto;
import hr.kricco.contractor.dto.ClientSummaryDto;
import hr.kricco.contractor.dto.CostsDto;
import hr.kricco.contractor.dto.CostsRequest;
import hr.kricco.contractor.dto.LocationDto;
import hr.kricco.contractor.dto.OrderDto;
import hr.kricco.contractor.dto.OrderRequest;
import hr.kricco.contractor.dto.OrderSummaryDto;
import hr.kricco.contractor.dto.ServicerDto;
import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.Costs;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.entity.Order;
import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Role;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.exception.BadRequestException;
import hr.kricco.contractor.exception.ConflictException;
import hr.kricco.contractor.exception.ForbiddenException;
import hr.kricco.contractor.exception.NotFoundException;
import hr.kricco.contractor.repository.BranchRepository;
import hr.kricco.contractor.repository.ClientRepository;
import hr.kricco.contractor.repository.LocationRepository;
import hr.kricco.contractor.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

// Orders for employees. Client users go through the portal (a later slice).
@Service
@RequiredArgsConstructor
public class OrderService {

    // In these statuses an order must have a client, a location and an order number
    private static final Set<OrderStatus> SUBMITTED_STATUSES =
            Set.of(OrderStatus.PENDING, OrderStatus.IN_PROGRESS, OrderStatus.RESOLVED);

    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final ClientRepository clientRepository;
    private final LocationRepository locationRepository;
    private final StatusTransitionService statusTransitionService;
    private final OrderNumberGenerator orderNumberGenerator;

    // ADMIN and OFFICE: all orders. SERVICER: assigned to them + all unassigned PENDING. Newest first.
    @Transactional(readOnly = true)
    public List<OrderSummaryDto> getOrders(User currentUser) {
        List<Order> orders;
        if (currentUser.getRole() == Role.SERVICER) {
            orders = orderRepository.findVisibleToServicer(currentUser.getId());
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDescIdDesc();
        }
        return orders.stream()
                .map(this::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(Long id, User currentUser) {
        Order order = findOrder(id);
        if (!canRead(order, currentUser)) {
            throw new ForbiddenException("You can't see this order");
        }
        return toDto(order, currentUser);
    }

    // A new order is always a DRAFT without a number. "Submit" is a status change to PENDING.
    @Transactional
    public OrderDto createOrder(OrderRequest request, User currentUser) {
        Order order = new Order();
        order.setStatus(OrderStatus.DRAFT);
        copyRequestFields(request, order);
        return toDto(orderRepository.save(order), currentUser);
    }

    // Full replace of the order data and the estimated costs, in any status
    @Transactional
    public OrderDto updateOrder(Long id, OrderRequest request, User currentUser) {
        Order order = findOrder(id);
        copyRequestFields(request, order);
        if (SUBMITTED_STATUSES.contains(order.getStatus())) {
            checkSubmittedFields(order);
        }
        return toDto(orderRepository.save(order), currentUser);
    }

    // In any status. Notes and photos are deleted with it once they exist.
    @Transactional
    public void deleteOrder(Long id) {
        Order order = findOrder(id);
        orderRepository.delete(order);
    }

    @Transactional
    public OrderDto changeStatus(Long id, OrderStatus newStatus, User currentUser) {
        Order order = findOrder(id);
        if (!canChange(order, currentUser)) {
            throw new ForbiddenException("You can't change this order");
        }
        applyStatus(order, newStatus);
        return toDto(orderRepository.save(order), currentUser);
    }

    // Every status change goes through here, also the ones from accept, assign and the portal (later slices)
    void applyStatus(Order order, OrderStatus newStatus) {
        if (!statusTransitionService.isAllowed(order.getStatus(), newStatus)) {
            throw new ConflictException("Status change from " + order.getStatus() + " to " + newStatus + " is not allowed");
        }
        if (newStatus == OrderStatus.IN_PROGRESS && order.getAssignedServicer() == null) {
            throw new ConflictException("Assign a servicer first");
        }
        if (SUBMITTED_STATUSES.contains(newStatus)) {
            checkSubmittedFields(order);
            // The first submit takes a number. An order that goes back to DRAFT and is submitted again keeps it.
            if (order.getOrderNumber() == null) {
                order.setOrderNumber(orderNumberGenerator.next());
            }
        }
        order.setStatus(newStatus);
    }

    private void checkSubmittedFields(Order order) {
        if (order.getClient() == null) {
            throw new BadRequestException("Client is required");
        }
        if (order.getLocation() == null) {
            throw new BadRequestException("Location is required");
        }
    }

    // Row check for reading. ADMIN and OFFICE see every order.
    private boolean canRead(Order order, User user) {
        if (isAdminOrOffice(user)) {
            return true;
        }
        return isAssignedTo(order, user) || isUnassignedPending(order);
    }

    // Row check for changing. A SERVICER may change only orders assigned to them.
    private boolean canChange(Order order, User user) {
        if (isAdminOrOffice(user)) {
            return true;
        }
        return isAssignedTo(order, user);
    }

    private boolean isAdminOrOffice(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.OFFICE;
    }

    private boolean isAssignedTo(Order order, User user) {
        User servicer = order.getAssignedServicer();
        return servicer != null && servicer.getId().equals(user.getId());
    }

    private boolean isUnassignedPending(Order order) {
        return order.getAssignedServicer() == null && order.getStatus() == OrderStatus.PENDING;
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

    // Full replace. Unknown IDs in the body are a 400, not a 404: the path itself is fine.
    private void copyRequestFields(OrderRequest request, Order order) {
        Client client = findClientOrNull(request.clientId());
        Location location = findLocationOrNull(request.locationId());
        checkLocationBelongsToClient(location, client);
        order.setBranch(findBranchOrNull(request.branchId()));
        order.setClient(client);
        order.setLocation(location);
        order.setContactPerson(request.contactPerson());
        order.setPhone(request.phone());
        order.setEmail(request.email());
        order.setDescription(request.description());
        order.setUrgency(request.urgency());
        order.setEstimatedCosts(toCosts(request.estimatedCosts()));
    }

    private void checkLocationBelongsToClient(Location location, Client client) {
        if (location == null) {
            return;
        }
        boolean sameClient = client != null && location.getClient().getId().equals(client.getId());
        if (!sameClient) {
            throw new BadRequestException("Location doesn't belong to the client");
        }
    }

    private Branch findBranchOrNull(Long branchId) {
        if (branchId == null) {
            return null;
        }
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new BadRequestException("Branch not found"));
    }

    private Client findClientOrNull(Long clientId) {
        if (clientId == null) {
            return null;
        }
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new BadRequestException("Client not found"));
    }

    private Location findLocationOrNull(Long locationId) {
        if (locationId == null) {
            return null;
        }
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new BadRequestException("Location not found"));
    }

    private Costs toCosts(CostsRequest request) {
        Costs costs = new Costs();
        if (request != null) {
            costs.setKm(request.km());
            costs.setWorkHours(request.workHours());
            costs.setNumberOfWorkers(request.numberOfWorkers());
            costs.setMaterialCost(request.materialCost());
        }
        return costs;
    }

    private OrderSummaryDto toSummaryDto(Order order) {
        Branch branch = order.getBranch();
        User servicer = order.getAssignedServicer();
        return new OrderSummaryDto(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getUrgency(),
                branch == null ? null : branch.getId(),
                branch == null ? null : branch.getName(),
                order.getClient() == null ? null : order.getClient().getName(),
                toLocationText(order.getLocation()),
                servicer == null ? null : servicer.getId(),
                servicer == null ? null : servicer.getDisplayName(),
                order.getCreatedAt());
    }

    private OrderDto toDto(Order order, User currentUser) {
        return new OrderDto(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                allowedNextStatuses(order, currentUser),
                order.getUrgency(),
                toBranchDto(order.getBranch()),
                toClientDto(order.getClient()),
                toLocationDto(order.getLocation()),
                order.getContactPerson(),
                order.getPhone(),
                order.getEmail(),
                order.getDescription(),
                toServicerDto(order.getAssignedServicer()),
                toCostsDto(order.getEstimatedCosts()),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    // The choices the UI shows. Empty if the user may not change the order.
    // Without a servicer, IN_PROGRESS is left out: applyStatus would refuse it.
    private List<OrderStatus> allowedNextStatuses(Order order, User currentUser) {
        if (!canChange(order, currentUser)) {
            return List.of();
        }
        boolean hasServicer = order.getAssignedServicer() != null;
        return statusTransitionService.allowedNext(order.getStatus()).stream()
                .filter(status -> hasServicer || status != OrderStatus.IN_PROGRESS)
                .toList();
    }

    private String toLocationText(Location location) {
        if (location == null) {
            return null;
        }
        return location.getAddress() + ", " + location.getCity();
    }

    private BranchDto toBranchDto(Branch branch) {
        if (branch == null) {
            return null;
        }
        return new BranchDto(branch.getId(), branch.getName(), branch.getCity());
    }

    private ClientSummaryDto toClientDto(Client client) {
        if (client == null) {
            return null;
        }
        return new ClientSummaryDto(client.getId(), client.getType(), client.getName());
    }

    private LocationDto toLocationDto(Location location) {
        if (location == null) {
            return null;
        }
        return new LocationDto(location.getId(), location.getName(), location.getAddress(), location.getCity());
    }

    private ServicerDto toServicerDto(User servicer) {
        if (servicer == null) {
            return null;
        }
        return new ServicerDto(servicer.getId(), servicer.getDisplayName());
    }

    // Hibernate loads an embedded object as null when all its columns are empty
    private CostsDto toCostsDto(Costs costs) {
        if (costs == null) {
            return new CostsDto(null, null, null, null, null);
        }
        return new CostsDto(
                costs.getKm(), costs.getWorkHours(), costs.getNumberOfWorkers(),
                costs.getTotalHours(), costs.getMaterialCost());
    }
}
