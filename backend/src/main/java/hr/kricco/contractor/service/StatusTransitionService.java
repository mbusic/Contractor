package hr.kricco.contractor.service;

import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.StatusTransition;
import hr.kricco.contractor.repository.StatusTransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Which status changes are allowed, read from the status_transitions table (domain-model C10)
@Service
@RequiredArgsConstructor
public class StatusTransitionService {

    private final StatusTransitionRepository statusTransitionRepository;

    @Transactional(readOnly = true)
    public boolean isAllowed(OrderStatus from, OrderStatus to) {
        return statusTransitionRepository.existsByFromStatusAndToStatus(from, to);
    }

    // In the enum's order (DRAFT, PENDING, IN_PROGRESS, RESOLVED, CANCELLED)
    @Transactional(readOnly = true)
    public List<OrderStatus> allowedNext(OrderStatus from) {
        return statusTransitionRepository.findByFromStatus(from).stream()
                .map(StatusTransition::getToStatus)
                .sorted()
                .toList();
    }
}
