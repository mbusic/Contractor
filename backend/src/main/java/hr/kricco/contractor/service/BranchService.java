package hr.kricco.contractor.service;

import hr.kricco.contractor.dto.BranchDto;
import hr.kricco.contractor.dto.BranchRequest;
import hr.kricco.contractor.entity.Branch;
import hr.kricco.contractor.exception.ConflictException;
import hr.kricco.contractor.exception.NotFoundException;
import hr.kricco.contractor.repository.BranchRepository;
import hr.kricco.contractor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<BranchDto> getAll() {
        return branchRepository.findAll(Sort.by("name")).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BranchDto getById(Long id) {
        return toDto(findBranch(id));
    }

    @Transactional
    public BranchDto create(BranchRequest request) {
        Branch branch = new Branch();
        copyRequestFields(request, branch);
        return toDto(branchRepository.save(branch));
    }

    @Transactional
    public BranchDto update(Long id, BranchRequest request) {
        Branch branch = findBranch(id);
        copyRequestFields(request, branch);
        return toDto(branchRepository.save(branch));
    }

    @Transactional
    public void delete(Long id) {
        Branch branch = findBranch(id);
        // Blocked instead of failing on the foreign key (domain-model Q3)
        if (userRepository.existsByBranchId(id)) {
            throw new ConflictException("Branch has users");
        }
        branchRepository.delete(branch);
    }

    private Branch findBranch(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Branch not found"));
    }

    // Full replace: a missing field in the request clears the value
    private void copyRequestFields(BranchRequest request, Branch branch) {
        branch.setName(request.name());
        branch.setCity(request.city());
    }

    private BranchDto toDto(Branch branch) {
        return new BranchDto(branch.getId(), branch.getName(), branch.getCity());
    }
}
