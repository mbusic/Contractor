package hr.kricco.contractor.service;

import hr.kricco.contractor.dto.BranchDto;
import hr.kricco.contractor.dto.ClientSummaryDto;
import hr.kricco.contractor.dto.CostsDto;
import hr.kricco.contractor.dto.CostsRequest;
import hr.kricco.contractor.dto.LocationDto;
import hr.kricco.contractor.dto.NoteDto;
import hr.kricco.contractor.dto.OrderDto;
import hr.kricco.contractor.dto.OrderRequest;
import hr.kricco.contractor.dto.OrderSummaryDto;
import hr.kricco.contractor.dto.PhotoDto;
import hr.kricco.contractor.dto.PortalOrderDto;
import hr.kricco.contractor.dto.PortalOrderRequest;
import hr.kricco.contractor.dto.PortalOrderSummaryDto;
import hr.kricco.contractor.dto.ServicerDto;
import hr.kricco.contractor.dto.UploadedFile;
import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.Costs;
import hr.kricco.contractor.entity.DocumentType;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.entity.Order;
import hr.kricco.contractor.entity.OrderNote;
import hr.kricco.contractor.entity.OrderPhoto;
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
import hr.kricco.contractor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

// Orders: the employee endpoints (/api/orders) and the client portal (/api/portal/orders).
@Service
@RequiredArgsConstructor
public class OrderService {

    // In these statuses an order must have a client, a location and an order number
    private static final Set<OrderStatus> SUBMITTED_STATUSES =
            Set.of(OrderStatus.PENDING, OrderStatus.IN_PROGRESS, OrderStatus.RESOLVED);

    private static final String ALREADY_TAKEN = "Order is already taken or not pending";

    private static final int MAX_PHOTOS = 6;

    private static final String CANT_OPEN_DOCUMENT = "You can't open this document";

    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final ClientRepository clientRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final StatusTransitionService statusTransitionService;
    private final OrderNumberGenerator orderNumberGenerator;
    private final FileStorageService fileStorageService;
    private final DocumentService documentService;

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
        VersionCheck.check(request.version(), order.getVersion());
        copyRequestFields(request, order);
        if (SUBMITTED_STATUSES.contains(order.getStatus())) {
            checkSubmittedFields(order);
        }
        return toDto(orderRepository.saveAndFlush(order), currentUser);
    }

    // In any status. Its notes and photo rows go with it (cascade), then the photo files are deleted.
    @Transactional
    public void deleteOrder(Long id) {
        Order order = findOrder(id);
        List<String> photoFiles = order.getPhotos().stream()
                .map(OrderPhoto::getFilename)
                .toList();
        orderRepository.delete(order);
        // Rows first: if they can't be deleted, the files are still there
        orderRepository.flush();
        photoFiles.forEach(fileStorageService::delete);
    }

    @Transactional
    public OrderDto changeStatus(Long id, OrderStatus newStatus, Long version, User currentUser) {
        Order order = findOrder(id);
        if (!canChange(order, currentUser)) {
            throw new ForbiddenException("You can't change this order");
        }
        VersionCheck.check(version, order.getVersion());
        applyStatus(order, newStatus);
        return toDto(orderRepository.saveAndFlush(order), currentUser);
    }

    // A servicer takes an unassigned PENDING order (first-line process)
    @Transactional
    public OrderDto acceptOrder(Long id, User currentUser) {
        Order order = findOrder(id);
        if (!isUnassignedPending(order)) {
            throw new ConflictException(ALREADY_TAKEN);
        }
        order.setAssignedServicer(userRepository.getReferenceById(currentUser.getId()));
        applyStatus(order, OrderStatus.IN_PROGRESS);
        return toDto(saveAcceptedOrder(order), currentUser);
    }

    // Two servicers who accept at the same time both pass the check in acceptOrder.
    // @Version lets only the first save through, and the second gets the same answer as if it came later.
    private Order saveAcceptedOrder(Order order) {
        try {
            return orderRepository.saveAndFlush(order);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException(ALREADY_TAKEN);
        }
    }

    // The office assigns a servicer to a PENDING order (it becomes IN_PROGRESS) or reassigns an IN_PROGRESS one
    @Transactional
    public OrderDto assignServicer(Long id, Long servicerId, Long version, User currentUser) {
        Order order = findOrder(id);
        VersionCheck.check(version, order.getVersion());
        User servicer = findActiveServicer(servicerId);
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setAssignedServicer(servicer);
            applyStatus(order, OrderStatus.IN_PROGRESS);
        } else if (order.getStatus() == OrderStatus.IN_PROGRESS) {
            order.setAssignedServicer(servicer);
        } else {
            throw new ConflictException("Only PENDING and IN_PROGRESS orders can be assigned");
        }
        return toDto(orderRepository.saveAndFlush(order), currentUser);
    }

    // Full replace of the actual costs, in any status. A SERVICER only on orders assigned to them.
    @Transactional
    public OrderDto updateActualCosts(Long id, CostsRequest costs, Long version, User currentUser) {
        Order order = findOrder(id);
        if (!canChange(order, currentUser)) {
            throw new ForbiddenException("You can't change this order");
        }
        VersionCheck.check(version, order.getVersion());
        order.setActualCosts(toCosts(costs));
        return toDto(orderRepository.saveAndFlush(order), currentUser);
    }

    // In any status. A SERVICER only on orders assigned to them.
    @Transactional
    public OrderDto addNote(Long id, String text, User currentUser) {
        Order order = findOrder(id);
        if (!canChange(order, currentUser)) {
            throw new ForbiddenException("You can't change this order");
        }
        OrderNote note = new OrderNote();
        note.setOrder(order);
        note.setAuthor(userRepository.getReferenceById(currentUser.getId()));
        note.setText(text);
        // Newest first, like @OrderBy on Order.notes
        order.getNotes().addFirst(note);
        // The flush inserts the note (cascade), so the response has its ID and createdAt
        return toDto(orderRepository.saveAndFlush(order), currentUser);
    }

    // In any status. A SERVICER only on orders assigned to them.
    @Transactional
    public OrderDto addPhoto(Long id, UploadedFile file, User currentUser) {
        Order order = findOrder(id);
        if (!canChange(order, currentUser)) {
            throw new ForbiddenException("You can't change this order");
        }
        storePhoto(order, file);
        // The flush inserts the photo row (cascade), so the response has its ID
        return toDto(orderRepository.saveAndFlush(order), currentUser);
    }

    @Transactional
    public void deletePhoto(Long orderId, Long photoId, User currentUser) {
        Order order = findOrder(orderId);
        if (!canChange(order, currentUser)) {
            throw new ForbiddenException("You can't change this order");
        }
        removePhoto(order, photoId);
    }

    // 404 if the photo isn't on this order
    private void removePhoto(Order order, Long photoId) {
        OrderPhoto photo = order.getPhotos().stream()
                .filter(candidate -> candidate.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Photo not found"));
        order.getPhotos().remove(photo);
        // Row first: if it can't be deleted, the file is still there
        orderRepository.flush();
        fileStorageService.delete(photo.getFilename());
    }

    // Two uploads at the same moment can get past the limit (accepted)
    private void storePhoto(Order order, UploadedFile file) {
        if (order.getPhotos().size() >= MAX_PHOTOS) {
            throw new BadRequestException("An order can have at most " + MAX_PHOTOS + " photos");
        }
        ImageType type = checkImage(file);
        OrderPhoto photo = new OrderPhoto();
        photo.setOrder(order);
        photo.setFilename(fileStorageService.store(file.content(), type.extension()));
        order.getPhotos().add(photo);
    }

    // The name and Content-Type give a quick, clear answer for a wrong file.
    // The first bytes decide, since the client can fake both. The stored extension comes from them.
    private ImageType checkImage(UploadedFile file) {
        boolean allowedName = ImageType.fromFileName(file.originalName()).isPresent();
        if (!allowedName || !ImageType.isAllowedMediaType(file.contentType())) {
            throw new BadRequestException("Only JPEG, PNG, GIF or WebP images are allowed");
        }
        return ImageType.detect(file.content())
                .orElseThrow(() -> new BadRequestException("The file is not a valid JPEG, PNG, GIF or WebP image"));
    }

    // ADMIN and OFFICE: every type. A SERVICER: only the work order, and only of an order assigned to them,
    // since it's the sheet they fill in on site. Any status: a draft prints "Nacrt" instead of a number.
    @Transactional(readOnly = true)
    public String getDocument(Long id, DocumentType type, User currentUser) {
        Order order = findOrder(id);
        boolean servicerWorkOrder = type == DocumentType.WORK_ORDER && isAssignedTo(order, currentUser);
        if (!isAdminOrOffice(currentUser) && !servicerWorkOrder) {
            throw new ForbiddenException(CANT_OPEN_DOCUMENT);
        }
        return documentService.render(order, type);
    }

    // For UserService, when a servicer is deactivated or gets another role. Their IN_PROGRESS orders go back
    // to PENDING, which clears the servicer, so other servicers can accept them. Other statuses keep the servicer.
    @Transactional
    public void releaseOrdersOf(User servicer) {
        List<Order> orders = orderRepository.findByAssignedServicerIdAndStatus(servicer.getId(), OrderStatus.IN_PROGRESS);
        for (Order order : orders) {
            applyStatus(order, OrderStatus.PENDING);
        }
    }

    // Client portal. A client user works only with their own client's orders: the ones created in the portal
    // or submitted (they have a number). The office's unsubmitted drafts stay hidden. Changes only while DRAFT.

    @Transactional(readOnly = true)
    public List<PortalOrderSummaryDto> getPortalOrders(User currentUser) {
        return orderRepository.findVisibleInPortal(clientIdOf(currentUser)).stream()
                .map(this::toPortalSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PortalOrderDto getPortalOrder(Long id, User currentUser) {
        return toPortalDto(findPortalOrder(id, currentUser));
    }

    // A new DRAFT for the user's client, marked as created in the portal. No number, branch or costs.
    @Transactional
    public PortalOrderDto createPortalOrder(PortalOrderRequest request, User currentUser) {
        Order order = new Order();
        order.setStatus(OrderStatus.DRAFT);
        order.setClient(clientRepository.getReferenceById(clientIdOf(currentUser)));
        order.setCreatedInPortal(true);
        copyPortalRequestFields(request, order, currentUser);
        return toPortalDto(orderRepository.save(order));
    }

    // Full replace of the form fields
    @Transactional
    public PortalOrderDto updatePortalOrder(Long id, PortalOrderRequest request, User currentUser) {
        Order order = findPortalOrder(id, currentUser);
        VersionCheck.check(request.version(), order.getVersion());
        checkDraft(order);
        copyPortalRequestFields(request, order, currentUser);
        return toPortalDto(orderRepository.saveAndFlush(order));
    }

    // DRAFT -> PENDING: 400 without a location, takes the order number
    @Transactional
    public PortalOrderDto submitPortalOrder(Long id, Long version, User currentUser) {
        Order order = findPortalOrder(id, currentUser);
        VersionCheck.check(version, order.getVersion());
        checkDraft(order);
        applyStatus(order, OrderStatus.PENDING);
        return toPortalDto(orderRepository.saveAndFlush(order));
    }

    @Transactional
    public PortalOrderDto addPortalPhoto(Long id, UploadedFile file, User currentUser) {
        Order order = findPortalOrder(id, currentUser);
        checkDraft(order);
        storePhoto(order, file);
        return toPortalDto(orderRepository.saveAndFlush(order));
    }

    @Transactional
    public void deletePortalPhoto(Long id, Long photoId, User currentUser) {
        Order order = findPortalOrder(id, currentUser);
        checkDraft(order);
        removePhoto(order, photoId);
    }

    // Every type except the work order: that one is the servicer's internal sheet (domain-model Q5)
    @Transactional(readOnly = true)
    public String getPortalDocument(Long id, DocumentType type, User currentUser) {
        Order order = findPortalOrder(id, currentUser);
        if (type == DocumentType.WORK_ORDER) {
            throw new ForbiddenException(CANT_OPEN_DOCUMENT);
        }
        return documentService.render(order, type);
    }

    // The user comes from a finished transaction: only the ID of its client proxy can be read
    private Long clientIdOf(User currentUser) {
        return currentUser.getClient().getId();
    }

    // 404 for an unknown ID. Another client's order, or an office draft of this client, is 403.
    private Order findPortalOrder(Long id, User currentUser) {
        Order order = findOrder(id);
        boolean ownClient = order.getClient() != null && order.getClient().getId().equals(clientIdOf(currentUser));
        boolean visible = order.isCreatedInPortal() || order.getOrderNumber() != null;
        if (!ownClient || !visible) {
            throw new ForbiddenException("You can't see this order");
        }
        return order;
    }

    private void checkDraft(Order order) {
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new ConflictException("Only a draft can be changed");
        }
    }

    // Full replace. The location must be one of the user's client's, else 400 (the ID is in the body).
    private void copyPortalRequestFields(PortalOrderRequest request, Order order, User currentUser) {
        order.setLocation(findOwnLocationOrNull(request.locationId(), currentUser));
        order.setContactPerson(request.contactPerson());
        order.setPhone(request.phone());
        order.setEmail(request.email());
        order.setDescription(request.description());
        order.setUrgency(request.urgency());
    }

    private Location findOwnLocationOrNull(Long locationId, User currentUser) {
        if (locationId == null) {
            return null;
        }
        return locationRepository.findByIdAndClientId(locationId, clientIdOf(currentUser))
                .orElseThrow(() -> new BadRequestException("Location not found"));
    }

    private PortalOrderSummaryDto toPortalSummaryDto(Order order) {
        return new PortalOrderSummaryDto(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getUrgency(),
                toLocationText(order.getLocation()),
                order.getCreatedAt());
    }

    // No costs, notes, servicer or branch: they're internal
    private PortalOrderDto toPortalDto(Order order) {
        return new PortalOrderDto(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getUrgency(),
                toLocationDto(order.getLocation()),
                order.getContactPerson(),
                order.getPhone(),
                order.getEmail(),
                order.getDescription(),
                order.getPhotos().stream().map(this::toPhotoDto).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getVersion());
    }

    // Every status change goes through here, also the ones from accept, assign, releaseOrdersOf and portal submit
    private void applyStatus(Order order, OrderStatus newStatus) {
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
        // PENDING means "waiting for a servicer", so a PENDING order never has one
        if (newStatus == OrderStatus.PENDING) {
            order.setAssignedServicer(null);
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

    // The servicer comes from the body, so an unknown ID is a 400. A deactivated one can't log in to see the order.
    private User findActiveServicer(Long servicerId) {
        User user = userRepository.findById(servicerId)
                .orElseThrow(() -> new BadRequestException("Servicer not found"));
        if (user.getRole() != Role.SERVICER) {
            throw new BadRequestException("User is not a servicer");
        }
        if (!user.isActive()) {
            throw new BadRequestException("Servicer is not active");
        }
        return user;
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
                toCostsDto(order.getActualCosts()),
                toCostDifference(order.getEstimatedCosts(), order.getActualCosts()),
                order.getNotes().stream().map(this::toNoteDto).toList(),
                order.getPhotos().stream().map(this::toPhotoDto).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getVersion());
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
        return new BranchDto(branch.getId(), branch.getName(), branch.getCity(), branch.getVersion());
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
        return new LocationDto(
                location.getId(), location.getName(), location.getAddress(), location.getCity(), location.getVersion());
    }

    private ServicerDto toServicerDto(User servicer) {
        if (servicer == null) {
            return null;
        }
        return new ServicerDto(servicer.getId(), servicer.getDisplayName());
    }

    private PhotoDto toPhotoDto(OrderPhoto photo) {
        return new PhotoDto(photo.getId(), "/api/files/" + photo.getFilename());
    }

    private NoteDto toNoteDto(OrderNote note) {
        User author = note.getAuthor();
        return new NoteDto(note.getId(), note.getText(), author.getId(), author.getDisplayName(), note.getCreatedAt());
    }

    private CostsDto toCostsDto(Costs costs) {
        Costs costsOrEmpty = orEmpty(costs);
        return new CostsDto(
                costsOrEmpty.getKm(), costsOrEmpty.getWorkHours(), costsOrEmpty.getNumberOfWorkers(),
                costsOrEmpty.getTotalHours(), costsOrEmpty.getMaterialCost());
    }

    // Actual - estimated for each field, null where either value is missing (domain-model PR5)
    private CostsDto toCostDifference(Costs estimated, Costs actual) {
        Costs estimatedOrEmpty = orEmpty(estimated);
        Costs actualOrEmpty = orEmpty(actual);
        return new CostsDto(
                difference(actualOrEmpty.getKm(), estimatedOrEmpty.getKm()),
                difference(actualOrEmpty.getWorkHours(), estimatedOrEmpty.getWorkHours()),
                difference(actualOrEmpty.getNumberOfWorkers(), estimatedOrEmpty.getNumberOfWorkers()),
                difference(actualOrEmpty.getTotalHours(), estimatedOrEmpty.getTotalHours()),
                difference(actualOrEmpty.getMaterialCost(), estimatedOrEmpty.getMaterialCost()));
    }

    // Hibernate loads an embedded object as null when all its columns are empty
    private Costs orEmpty(Costs costs) {
        return costs == null ? new Costs() : costs;
    }

    private Integer difference(Integer actual, Integer estimated) {
        if (actual == null || estimated == null) {
            return null;
        }
        return actual - estimated;
    }

    private BigDecimal difference(BigDecimal actual, BigDecimal estimated) {
        if (actual == null || estimated == null) {
            return null;
        }
        return actual.subtract(estimated);
    }
}
