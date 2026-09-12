package hr.kricco.contractor.controller;

import hr.kricco.contractor.dto.OrderDto;
import hr.kricco.contractor.dto.OrderRequest;
import hr.kricco.contractor.dto.OrderSummaryDto;
import hr.kricco.contractor.dto.StatusChangeRequest;
import hr.kricco.contractor.security.UserPrincipal;
import hr.kricco.contractor.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Orders for employees. Which orders a SERVICER may see or change is checked in OrderService.
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE', 'SERVICER')")
    public List<OrderSummaryDto> getOrders(@AuthenticationPrincipal UserPrincipal principal) {
        return orderService.getOrders(principal.getUser());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE', 'SERVICER')")
    public OrderDto getOrder(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.getOrder(id, principal.getUser());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public OrderDto create(@Valid @RequestBody OrderRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.createOrder(request, principal.getUser());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public OrderDto update(@PathVariable Long id, @Valid @RequestBody OrderRequest request,
                           @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.updateOrder(id, request, principal.getUser());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE')")
    public void delete(@PathVariable Long id) {
        orderService.deleteOrder(id);
    }

    // "Submit" of a draft is a change to PENDING
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICE', 'SERVICER')")
    public OrderDto changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.changeStatus(id, request.status(), request.version(), principal.getUser());
    }
}
