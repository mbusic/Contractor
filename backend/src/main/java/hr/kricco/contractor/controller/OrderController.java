package hr.kricco.contractor.controller;

import hr.kricco.contractor.dto.ActualCostsRequest;
import hr.kricco.contractor.dto.AssignmentRequest;
import hr.kricco.contractor.dto.NoteRequest;
import hr.kricco.contractor.dto.OrderDto;
import hr.kricco.contractor.dto.OrderRequest;
import hr.kricco.contractor.dto.OrderSummaryDto;
import hr.kricco.contractor.dto.PortalOrderDto;
import hr.kricco.contractor.dto.PortalOrderRequest;
import hr.kricco.contractor.dto.PortalOrderSummaryDto;
import hr.kricco.contractor.dto.StatusChangeRequest;
import hr.kricco.contractor.dto.SubmitRequest;
import hr.kricco.contractor.dto.UploadedFile;
import hr.kricco.contractor.security.UserPrincipal;
import hr.kricco.contractor.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

// Orders. Employees use /api/orders, client users the portal paths /api/portal/orders.
// Which orders a SERVICER or a client user may see or change is checked in OrderService.
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE', 'SERVICER')")
    public List<OrderSummaryDto> getOrders(@AuthenticationPrincipal UserPrincipal principal) {
        return orderService.getOrders(principal.getUser());
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE', 'SERVICER')")
    public OrderDto getOrder(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.getOrder(id, principal.getUser());
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public OrderDto create(@Valid @RequestBody OrderRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.createOrder(request, principal.getUser());
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public OrderDto update(@PathVariable Long id, @Valid @RequestBody OrderRequest request,
                           @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.updateOrder(id, request, principal.getUser());
    }

    @DeleteMapping("/orders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public void delete(@PathVariable Long id) {
        orderService.deleteOrder(id);
    }

    // "Submit" of a draft is a change to PENDING
    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE', 'SERVICER')")
    public OrderDto changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.changeStatus(id, request.status(), request.version(), principal.getUser());
    }

    // A servicer takes an unassigned PENDING order. No body, so no version: @Version stops two servicers at once.
    @PostMapping("/orders/{id}/accept")
    @PreAuthorize("hasRole('SERVICER')")
    public OrderDto accept(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.acceptOrder(id, principal.getUser());
    }

    @PutMapping("/orders/{id}/assignment")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public OrderDto assign(@PathVariable Long id, @Valid @RequestBody AssignmentRequest request,
                           @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.assignServicer(id, request.servicerId(), request.version(), principal.getUser());
    }

    // Separate from PUT order, so a servicer can enter the costs without editing the rest of the order
    @PutMapping("/orders/{id}/actual-costs")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE', 'SERVICER')")
    public OrderDto updateActualCosts(@PathVariable Long id, @Valid @RequestBody ActualCostsRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.updateActualCosts(id, request.costs(), request.version(), principal.getUser());
    }

    // No version: a note is a new row, the order itself doesn't change
    @PostMapping("/orders/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE', 'SERVICER')")
    public OrderDto addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest request,
                            @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.addNote(id, request.text(), principal.getUser());
    }

    // Multipart with one part "file". Copied into UploadedFile, so OrderService doesn't depend on Spring Web.
    @PostMapping(path = "/orders/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE', 'SERVICER')")
    public OrderDto addPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                             @AuthenticationPrincipal UserPrincipal principal) throws IOException {
        UploadedFile upload = new UploadedFile(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return orderService.addPhoto(id, upload, principal.getUser());
    }

    @DeleteMapping("/orders/{id}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE', 'SERVICER')")
    public void deletePhoto(@PathVariable Long id, @PathVariable Long photoId,
                            @AuthenticationPrincipal UserPrincipal principal) {
        orderService.deletePhoto(id, photoId, principal.getUser());
    }

    // Client portal: only CLIENT users, with their own request and response shapes (rest-api.md [A2]).
    // The document endpoint comes with the document views (roadmap step 9).

    @GetMapping("/portal/orders")
    @PreAuthorize("hasRole('CLIENT')")
    public List<PortalOrderSummaryDto> getPortalOrders(@AuthenticationPrincipal UserPrincipal principal) {
        return orderService.getPortalOrders(principal.getUser());
    }

    @GetMapping("/portal/orders/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public PortalOrderDto getPortalOrder(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.getPortalOrder(id, principal.getUser());
    }

    @PostMapping("/portal/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public PortalOrderDto createPortalOrder(@Valid @RequestBody PortalOrderRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.createPortalOrder(request, principal.getUser());
    }

    @PutMapping("/portal/orders/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public PortalOrderDto updatePortalOrder(@PathVariable Long id, @Valid @RequestBody PortalOrderRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.updatePortalOrder(id, request, principal.getUser());
    }

    @PostMapping("/portal/orders/{id}/submit")
    @PreAuthorize("hasRole('CLIENT')")
    public PortalOrderDto submitPortalOrder(@PathVariable Long id, @Valid @RequestBody SubmitRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.submitPortalOrder(id, request.version(), principal.getUser());
    }

    @PostMapping(path = "/portal/orders/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public PortalOrderDto addPortalPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                         @AuthenticationPrincipal UserPrincipal principal) throws IOException {
        UploadedFile upload = new UploadedFile(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return orderService.addPortalPhoto(id, upload, principal.getUser());
    }

    @DeleteMapping("/portal/orders/{id}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CLIENT')")
    public void deletePortalPhoto(@PathVariable Long id, @PathVariable Long photoId,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        orderService.deletePortalPhoto(id, photoId, principal.getUser());
    }
}
