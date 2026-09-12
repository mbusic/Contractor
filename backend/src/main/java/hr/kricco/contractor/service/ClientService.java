package hr.kricco.contractor.service;

import hr.kricco.contractor.dto.ClientDto;
import hr.kricco.contractor.dto.ClientRequest;
import hr.kricco.contractor.dto.LocationDto;
import hr.kricco.contractor.dto.LocationRequest;
import hr.kricco.contractor.entity.Client;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.entity.User;
import hr.kricco.contractor.exception.ConflictException;
import hr.kricco.contractor.exception.NotFoundException;
import hr.kricco.contractor.repository.ClientRepository;
import hr.kricco.contractor.repository.LocationRepository;
import hr.kricco.contractor.repository.OrderRepository;
import hr.kricco.contractor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<ClientDto> getAll() {
        return clientRepository.findAll(Sort.by("name")).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientDto getById(Long id) {
        return toDto(findClient(id));
    }

    @Transactional
    public ClientDto create(ClientRequest request) {
        Client client = new Client();
        copyRequestFields(request, client);
        return toDto(clientRepository.save(client));
    }

    // Full replace of the client fields. Locations stay as they are.
    @Transactional
    public ClientDto update(Long id, ClientRequest request) {
        Client client = findClient(id);
        VersionCheck.check(request.version(), client.getVersion());
        copyRequestFields(request, client);
        return toDto(clientRepository.saveAndFlush(client));
    }

    // Its locations are deleted with it (cascade). Blocked while users or orders point to the client (domain-model Q3).
    @Transactional
    public void delete(Long id) {
        Client client = findClient(id);
        if (userRepository.existsByClientId(id)) {
            throw new ConflictException("Client has users");
        }
        if (orderRepository.existsByClientId(id)) {
            throw new ConflictException("Client has orders");
        }
        clientRepository.delete(client);
    }

    @Transactional
    public LocationDto addLocation(Long clientId, LocationRequest request) {
        Client client = findClient(clientId);
        Location location = new Location();
        location.setClient(client);
        copyRequestFields(request, location);
        client.getLocations().add(location);
        return toDto(locationRepository.save(location));
    }

    @Transactional
    public LocationDto updateLocation(Long clientId, Long locationId, LocationRequest request) {
        Location location = findLocation(clientId, locationId);
        VersionCheck.check(request.version(), location.getVersion());
        copyRequestFields(request, location);
        return toDto(locationRepository.saveAndFlush(location));
    }

    // Removed through the client's list, so orphan removal deletes it.
    // Deleting only the location would fail if the client's list is already loaded: the list would save it again.
    @Transactional
    public void deleteLocation(Long clientId, Long locationId) {
        Location location = findLocation(clientId, locationId);
        if (orderRepository.existsByLocationId(locationId)) {
            throw new ConflictException("Location is used by orders");
        }
        location.getClient().getLocations().remove(location);
    }

    // Client portal: the locations of the client user's own client

    @Transactional(readOnly = true)
    public List<LocationDto> getPortalLocations(User currentUser) {
        return getById(clientIdOf(currentUser)).locations();
    }

    // A new work site, e.g. for an order at a new address
    @Transactional
    public LocationDto addPortalLocation(LocationRequest request, User currentUser) {
        return addLocation(clientIdOf(currentUser), request);
    }

    // The user comes from a finished transaction: only the ID of its client proxy can be read
    private Long clientIdOf(User currentUser) {
        return currentUser.getClient().getId();
    }

    private Client findClient(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Client not found"));
    }

    // A location of another client counts as not found
    private Location findLocation(Long clientId, Long locationId) {
        return locationRepository.findByIdAndClientId(locationId, clientId)
                .orElseThrow(() -> new NotFoundException("Location not found"));
    }

    // Full replace: a missing field in the request clears the value
    private void copyRequestFields(ClientRequest request, Client client) {
        client.setType(request.type());
        client.setName(request.name());
        client.setContactPerson(request.contactPerson());
        client.setPhone(request.phone());
        client.setEmail(request.email());
        client.setAddress(request.address());
    }

    private void copyRequestFields(LocationRequest request, Location location) {
        location.setName(request.name());
        location.setAddress(request.address());
        location.setCity(request.city());
    }

    private ClientDto toDto(Client client) {
        List<LocationDto> locations = client.getLocations().stream()
                .map(this::toDto)
                .toList();
        return new ClientDto(
                client.getId(), client.getType(), client.getName(), client.getContactPerson(),
                client.getPhone(), client.getEmail(), client.getAddress(), locations, client.getVersion());
    }

    private LocationDto toDto(Location location) {
        return new LocationDto(
                location.getId(), location.getName(), location.getAddress(), location.getCity(), location.getVersion());
    }
}
