package com.api.inventory.controller;

import com.api.inventory.entity.CustomerAddress;
import com.api.inventory.repository.CustomerAddressRepository;
import com.api.inventory.repository.DeliveryAreaRepository;
import com.api.inventory.security.CurrentUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * The signed-in person's saved delivery addresses (Home, Office, ...). Checkout offers them, so the address, the
 * phone and the place on the map are not typed again. At most 10; one is the default (the first one saved is).
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    static final int MAX_ADDRESSES = 10;

    private final CustomerAddressRepository addresses;
    private final DeliveryAreaRepository areas;

    public AddressController(CustomerAddressRepository addresses, DeliveryAreaRepository areas) {
        this.addresses = addresses;
        this.areas = areas;
    }

    /** areaId, or latitude + longitude (a phone location), or none of them. */
    public record AddressForm(String label, String phone, String address, Long areaId, Double latitude, Double longitude,
                              String pointLabel, Boolean makeDefault) {
    }

    public record AddressView(Long id, String label, String phone, String address, Long areaId, Double latitude,
                              Double longitude, String pointLabel, boolean isDefault) {
        static AddressView of(CustomerAddress a) {
            return new AddressView(a.getId(), a.getLabel(), a.getPhone(), a.getAddress(), a.getAreaId(), a.getLatitude(),
                    a.getLongitude(), a.getPointLabel(), a.isDefaultAddress());
        }
    }

    /** The default first, then the newest. */
    @GetMapping
    public List<AddressView> mine() {
        return addresses.findByUserEmailIgnoreCaseOrderByDefaultAddressDescCreatedAtDesc(CurrentUser.email()).stream()
                .map(AddressView::of).toList();
    }

    @PostMapping
    @Transactional
    public AddressView add(@RequestBody AddressForm form) {
        String me = CurrentUser.email();
        long count = addresses.countByUserEmailIgnoreCase(me);
        if (count >= MAX_ADDRESSES) {
            throw new IllegalStateException("You can keep " + MAX_ADDRESSES + " addresses. Remove one first.");
        }
        CustomerAddress a = new CustomerAddress();
        a.setUserEmail(me);
        a.setCreatedAt(Instant.now());
        fill(a, form);
        boolean makeDefault = count == 0 || Boolean.TRUE.equals(form.makeDefault());
        CustomerAddress saved = addresses.save(a);
        if (makeDefault) {
            setDefault(me, saved.getId());
        }
        return AddressView.of(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public AddressView change(@PathVariable Long id, @RequestBody AddressForm form) {
        String me = CurrentUser.email();
        CustomerAddress a = own(id, me);
        fill(a, form);
        a.setUpdatedAt(Instant.now());
        addresses.save(a);
        if (Boolean.TRUE.equals(form.makeDefault())) {
            setDefault(me, id);
        }
        return AddressView.of(a);
    }

    @PostMapping("/{id}/default")
    @Transactional
    public List<AddressView> makeDefault(@PathVariable Long id) {
        String me = CurrentUser.email();
        own(id, me);
        setDefault(me, id);
        return mine();
    }

    /** Removing the default makes the newest other address the default. */
    @DeleteMapping("/{id}")
    @Transactional
    public List<AddressView> remove(@PathVariable Long id) {
        String me = CurrentUser.email();
        CustomerAddress a = own(id, me);
        addresses.delete(a);
        addresses.flush();
        if (a.isDefaultAddress()) {
            addresses.findByUserEmailIgnoreCaseOrderByDefaultAddressDescCreatedAtDesc(me).stream().findFirst()
                    .ifPresent(next -> setDefault(me, next.getId()));
        }
        return mine();
    }

    private CustomerAddress own(Long id, String me) {
        return addresses.findByIdAndUserEmailIgnoreCase(id, me)
                .orElseThrow(() -> new IllegalStateException("That address was not found."));
    }

    private void setDefault(String me, Long id) {
        for (CustomerAddress a : addresses.findByUserEmailIgnoreCaseOrderByDefaultAddressDescCreatedAtDesc(me)) {
            boolean wanted = a.getId().equals(id);
            if (a.isDefaultAddress() != wanted) {
                a.setDefaultAddress(wanted);
                addresses.save(a);
            }
        }
    }

    private void fill(CustomerAddress a, AddressForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Fill in the address.");
        }
        String label = trim(form.label());
        a.setLabel(label.isEmpty() ? "Home" : cut(label, 30));
        String phone = trim(form.phone());
        if (!phone.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("Enter an 8-digit phone number.");
        }
        a.setPhone(phone);
        String address = trim(form.address());
        if (address.isEmpty()) {
            throw new IllegalArgumentException("Enter the address (house, building, a landmark).");
        }
        if (address.length() > 300) {
            throw new IllegalArgumentException("The address is too long (300 characters at most).");
        }
        a.setAddress(address);
        // where on the map: a delivery area we know, or a phone location in Bhutan; otherwise nothing
        a.setAreaId(null);
        a.setLatitude(null);
        a.setLongitude(null);
        if (form.areaId() != null && areas.existsById(form.areaId())) {
            a.setAreaId(form.areaId());
        } else if (form.latitude() != null && form.longitude() != null
                && form.latitude() >= 26 && form.latitude() <= 29 && form.longitude() >= 88 && form.longitude() <= 93) {
            a.setLatitude(form.latitude());
            a.setLongitude(form.longitude());
        }
        String pointLabel = trim(form.pointLabel());
        a.setPointLabel(a.getAreaId() == null && a.getLatitude() == null || pointLabel.isEmpty() ? null : cut(pointLabel, 120));
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
