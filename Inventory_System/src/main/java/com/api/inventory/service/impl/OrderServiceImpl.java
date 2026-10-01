package com.api.inventory.service.impl;

import com.api.inventory.dto.OrderItemResponseDTO;
import com.api.inventory.dto.OrderRequestDTO;
import com.api.inventory.dto.OrderVerificationDTO;
import com.api.inventory.dto.PosSaleRequestDTO;
import com.api.inventory.dto.PosSaleRequestDTO.ItemQty;
import com.api.inventory.dto.TaxInfoDTO;
import com.api.inventory.entity.*;
import com.api.inventory.exception.OrderNotFoundException;
import com.api.inventory.exception.ResourceNotFoundException;
import com.api.inventory.repository.*;
import com.api.inventory.service.OrderService;
import com.api.inventory.service.PaymentService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

	@Autowired
    private OrderRepository orderRepository;
	
	@Autowired
	private TaxDetailRepository taxDetailRepository;
	
	@Autowired
    private OrderItemRepository orderItemRepository;
	
	@Autowired
    private InventoryStockRepository inventoryStockRepository;
	
	@Autowired
    private TransactionRepository transactionRepository;
	
	@Autowired
    private ItemMasterRepository itemMasterRepository;
	@Autowired
	private  PaymentService paymentService; 
	@Autowired
	private ShipmentRepository shipmentRepository;


	@Transactional
	public Order createInPersonSale(PosSaleRequestDTO request) {
	    if (request.getItems() == null || request.getItems().isEmpty()) {
	        throw new IllegalArgumentException("At least one item is required");
	    }

	    BigDecimal subtotalBeforeDiscount = BigDecimal.ZERO; // MRP-based total
	    BigDecimal totalAfterDiscount = BigDecimal.ZERO;     // Final price total
	    BigDecimal totalDiscountAmount = BigDecimal.ZERO;   // ← What we'll save
	    List<OrderItem> orderItemsToSave = new ArrayList<>();

	    for (ItemQty item : request.getItems()) {
	        ItemMaster master = itemMasterRepository.findById(item.getItemId())
	                .orElseThrow(() -> new RuntimeException("Item not found: " + item.getItemId()));

	        InventoryStock stock = inventoryStockRepository.findByItemId(item.getItemId())
	                .orElseThrow(() -> new RuntimeException("Stock not initialized"));
	        if (stock.getCurrentQuantity() < item.getQuantity()) {
	            throw new IllegalStateException("Insufficient stock for item: " + master.getItemName());
	        }

	        // Get MRP (from request or DB)
	        BigDecimal mrp = (item.getMrp() != null && item.getMrp().compareTo(BigDecimal.ZERO) > 0)
	                ? item.getMrp()
	                : master.getMrp();

	        BigDecimal discountPercent = (item.getDiscountPercent() != null)
	                ? item.getDiscountPercent()
	                : BigDecimal.ZERO;

	        // Compute unit price after discount
	        BigDecimal unitPrice;
	        if (mrp != null && mrp.compareTo(BigDecimal.ZERO) > 0) {
	            BigDecimal discountMultiplier = BigDecimal.ONE
	                .subtract(discountPercent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
	            unitPrice = mrp.multiply(discountMultiplier).setScale(2, RoundingMode.HALF_UP);
	        } else {
	            unitPrice = master.getSellingPrice() != null ? master.getSellingPrice() : BigDecimal.ZERO;
	        }

	        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
	            throw new IllegalStateException("Invalid price for item: " + master.getItemName());
	        }

	        // Calculate line totals
	        BigDecimal lineMrpTotal = (mrp != null ? mrp : BigDecimal.ZERO)
	                .multiply(BigDecimal.valueOf(item.getQuantity()));
	        BigDecimal lineFinalTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
	        BigDecimal lineDiscount = lineMrpTotal.subtract(lineFinalTotal);

	        subtotalBeforeDiscount = subtotalBeforeDiscount.add(lineMrpTotal);
	        totalAfterDiscount = totalAfterDiscount.add(lineFinalTotal);
	        totalDiscountAmount = totalDiscountAmount.add(lineDiscount);

	        // Save order item
	        OrderItem orderItem = new OrderItem();
	        orderItem.setItemId(item.getItemId());
	        orderItem.setQuantity(item.getQuantity());
	        orderItem.setUnitPrice(unitPrice);
	        orderItemsToSave.add(orderItem);
	    }

	    // No additional order-level discount in POS
	    BigDecimal amountAfterDiscount = totalAfterDiscount;

	    // Calculate TAX on discounted amount
	    BigDecimal totalTax = BigDecimal.ZERO;
	    List<TaxDetail> taxDetailsToSave = new ArrayList<>();
	    if (request.getTaxes() != null) {
	        for (TaxInfoDTO taxInfo : request.getTaxes()) {
	            if (taxInfo.getRate() != null && taxInfo.getRate() > 0) {
	                BigDecimal rate = BigDecimal.valueOf(taxInfo.getRate());
	                BigDecimal taxAmount = amountAfterDiscount
	                    .multiply(rate)
	                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
	                totalTax = totalTax.add(taxAmount);

	                TaxDetail detail = new TaxDetail();
	                detail.setTaxType(taxInfo.getType());
	                detail.setRate(rate);
	                detail.setAmount(taxAmount);
	                taxDetailsToSave.add(detail);
	            }
	        }
	    }

	    // Final total = discounted amount + tax
	    BigDecimal finalTotal = amountAfterDiscount.add(totalTax);

	    // Save ORDER
	    Order order = new Order();
	    order.setCustomerName(request.getCustomerName());
	    order.setCustomerPhone(request.getCustomerPhone());
	    order.setPaymentMethod(request.getPaymentMethod());
	    order.setOrderStatus("COMPLETED");
	    order.setPaymentStatus("PAID");
	    order.setSource("POS");
	    order.setTotalAmount(finalTotal);
	    order.setDiscountAmount(totalDiscountAmount); // ✅ Now stores real discount!
	    order.setTaxAmount(totalTax);
	    order.setCreatedAt(LocalDateTime.now());
	    order.setUpdatedAt(LocalDateTime.now());

	    Order savedOrder = orderRepository.save(order);

	    // Save tax details
	    for (TaxDetail detail : taxDetailsToSave) {
	        detail.setOrder(savedOrder);
	    }
	    taxDetailRepository.saveAll(taxDetailsToSave);

	    // Save order items, deduct stock, create transactions
	    for (OrderItem orderItem : orderItemsToSave) {
	        orderItem.setOrderId(savedOrder.getOrderId());
	        orderItemRepository.save(orderItem);

	        inventoryStockRepository.adjustStockByDelta(orderItem.getItemId(), -orderItem.getQuantity());

	        Transaction tx = new Transaction();
	        tx.setItemId(orderItem.getItemId());
	        tx.setTransactionType("SALE");
	        tx.setQuantity(orderItem.getQuantity());
	        tx.setUnitPrice(orderItem.getUnitPrice());
	        tx.setCustomerOrSupplier(request.getCustomerName());
	        tx.setReferenceId(savedOrder.getOrderId());
	        tx.setReferenceType("POS_SALE");
	        tx.setCreatedAt(LocalDateTime.now());
	        transactionRepository.save(tx);
	    }

	    return savedOrder;
	}
	
	@Override
	@Transactional
	public Order createOrder(OrderRequestDTO dto) {
	    List<OrderItem> orderItems = new ArrayList<>();
	    BigDecimal totalAmount = BigDecimal.ZERO;

	    for (OrderRequestDTO.Item item : dto.getNormalizedItems()) {
	        ItemMaster itemMaster = itemMasterRepository.findById(item.getItemId())
	            .orElseThrow(() -> new RuntimeException("Item not found: " + item.getItemId()));

	        BigDecimal unitPrice = itemMaster.getSellingPrice();
	        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
	        totalAmount = totalAmount.add(lineTotal);

	        OrderItem orderItem = new OrderItem();
	        orderItem.setOrderId(null);
	        orderItem.setItemId(item.getItemId());
	        orderItem.setQuantity(item.getQuantity());
	        orderItem.setUnitPrice(unitPrice);
	        orderItems.add(orderItem);
	    }

	    // ✅ Save order with ALL fields
	    Order order = new Order();
	    order.setCustomerName(dto.getCustomerName());
	    order.setCustomerEmail(dto.getCustomerEmail());   // ← ADD THIS
	    order.setCustomerPhone(dto.getCustomerPhone());
	    order.setAddress(dto.getAddress());               // ← ADD THIS
	    order.setOrderStatus("CREATED");
	    order.setPaymentStatus("PENDING");
	    order.setTotalAmount(totalAmount);
	    order.setCreatedAt(LocalDateTime.now());
	    order.setUpdatedAt(LocalDateTime.now());
	    order.setSource("ONLINE");
	    
	    Order savedOrder = orderRepository.save(order);

	    for (OrderItem item : orderItems) {
	        item.setOrderId(savedOrder.getOrderId());
	        orderItemRepository.save(item);
	    }

	    return savedOrder;
	}
	
	@Transactional
	public void processOrderConfirmation(Long orderId) {
	    Order order = orderRepository.findById(orderId)
	            .orElseThrow(() -> new RuntimeException("Order not found"));

	    // Optional: re-validate state (safe guard)
	    if (!"CREATED".equals(order.getOrderStatus())) {
	        throw new IllegalStateException("Order must be in CREATED state to confirm");
	    }
	    if (!"PAID".equals(order.getPaymentStatus()) && !"PARTIALLY_PAID".equals(order.getPaymentStatus())) {
	        throw new IllegalStateException("Payment must be PAID or PARTIALLY_PAID");
	    }

	    List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

	    // ✅ Validate stock
	    for (OrderItem item : items) {
	        InventoryStock stock = inventoryStockRepository.findByItemId(item.getItemId())
	                .orElseThrow(() -> new RuntimeException("Stock not initialized for item: " + item.getItemId()));
	        if (stock.getCurrentQuantity() < item.getQuantity()) {
	            throw new IllegalStateException("Insufficient stock for item ID: " + item.getItemId());
	        }
	    }

	    // ✅ Deduct stock & record transactions
	    for (OrderItem item : items) {
	        inventoryStockRepository.adjustStockByDelta(item.getItemId(), -item.getQuantity());

	        Transaction tx = new Transaction();
	        tx.setItemId(item.getItemId());
	        tx.setTransactionType("SALE");
	        tx.setQuantity(item.getQuantity());
	        tx.setUnitPrice(item.getUnitPrice());
	        tx.setCustomerOrSupplier(order.getCustomerName());
	        tx.setReferenceId(orderId);
	        tx.setReferenceType("ORDER");
	        tx.setCreatedAt(LocalDateTime.now());
	        transactionRepository.save(tx);
	    }
	}
	

	@Override
	public List<Order> getPosSales() {
	    return orderRepository.findBySource("POS");
	}

	@Override
	public List<Order> getOnlineOrders() {
	    return orderRepository.findBySource("ONLINE");
	}
	
    @Override
    @Transactional
    public void confirmOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!"PAID".equals(order.getPaymentStatus())) {
            throw new IllegalStateException("Payment must be completed before confirming order");
        }
        Set<String> allowedPaymentStatuses = Set.of("PAID", "PARTIALLY_PAID");
        
        if (!allowedPaymentStatuses.contains(order.getPaymentStatus())) {
            throw new IllegalStateException("Order payment status must be PAID or PARTIALLY_PAID to confirm");
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

        // ✅ Validate stock for all items
        for (OrderItem item : items) {
            InventoryStock stock = inventoryStockRepository.findByItemId(item.getItemId())
                    .orElseThrow(() -> new RuntimeException("Stock not initialized for item"));
            if (stock.getCurrentQuantity() < item.getQuantity()) {
                throw new IllegalStateException(
                        "Insufficient stock for item ID: " + item.getItemId());
            }
        }

        // ✅ Reduce stock and record transactions
        for (OrderItem item : items) {
            // Reduce stock
            inventoryStockRepository.adjustStockByDelta(item.getItemId(), -item.getQuantity());

            // Record SALE transaction with order reference
            Transaction tx = new Transaction();
            tx.setItemId(item.getItemId());
            tx.setTransactionType("SALE");
            tx.setQuantity(item.getQuantity());
            tx.setUnitPrice(item.getUnitPrice());
            tx.setCustomerOrSupplier(order.getCustomerName());
            tx.setReferenceId(orderId);        // ← Link to order
            tx.setReferenceType("ORDER");      // ← Type = ORDER
            tx.setCreatedAt(LocalDateTime.now());
            transactionRepository.save(tx);
        }

        // ✅ Update order status
        order.setOrderStatus("CONFIRMED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }
    
    public void confirmOrderWithUser(Long orderId, String status, String note, String updatedBy) {
        Order order = findById(orderId);

        if ("CONFIRMED".equals(status)) {
            if (!"CREATED".equals(order.getOrderStatus())) {
                throw new IllegalStateException("Order must be in CREATED state");
            }
            if (!"PAID".equals(order.getPaymentStatus()) && !"PARTIALLY_PAID".equals(order.getPaymentStatus())) {
                throw new IllegalStateException("Payment must be PAID or PARTIALLY_PAID");
            }
            processOrderConfirmation(orderId);
        }

        order.setOrderStatus(status);
        order.setNote(note);
        order.setUpdatedBy(updatedBy); // ✅ Now valid — it's a parameter
        order.setUpdatedAt(LocalDateTime.now());
        save(order);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found"));

        if ("CANCELLED".equals(order.getOrderStatus())) {
            return;
        }

        if ("CONFIRMED".equals(order.getOrderStatus())) {
            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : items) {
                inventoryStockRepository.adjustStockByDelta(item.getItemId(), item.getQuantity());
                
                Transaction tx = new Transaction();
                tx.setItemId(item.getItemId());
                tx.setTransactionType("SALE_RETURN");
                tx.setQuantity(item.getQuantity());
                tx.setUnitPrice(item.getUnitPrice());
                tx.setCustomerOrSupplier(order.getCustomerName());
                tx.setReferenceId(orderId);
                tx.setReferenceType("ORDER_CANCEL");
                tx.setCreatedAt(LocalDateTime.now());
                transactionRepository.save(tx);
            }
        }

        order.setOrderStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void completeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found"));

        // ✅ Allow completion only if order is SHIPPED
        if (!"SHIPPED".equals(order.getOrderStatus())) {
            throw new IllegalStateException("Only SHIPPED orders can be completed");
        }

        order.setOrderStatus("COMPLETED"); 
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    @Override
    public List<Order> getOrdersByCustomerEmail(String email) {
        return orderRepository.findByCustomerEmail(email);
    }

 // OrderServiceImpl.java

    @Override
    @Transactional(readOnly = true)
    public List<OrderItemResponseDTO> getOrderItemsByOrderId(Long orderId) {
        // Verify order exists (optional but good practice)
        if (!orderRepository.existsById(orderId)) {
            throw new RuntimeException("Order not found: " + orderId);
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        
        return items.stream().map(item -> {
            OrderItemResponseDTO dto = new OrderItemResponseDTO();
            dto.setItemId(item.getItemId());
            dto.setQuantity(item.getQuantity());
            dto.setUnitPrice(item.getUnitPrice());
            
            // Fetch item name and image from ItemMaster
            ItemMaster itemMaster = itemMasterRepository.findById(item.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found: " + item.getItemId()));
            dto.setItemName(itemMaster.getItemName());
            dto.setImagePath(itemMaster.getImagePath()); // nullable is OK
            
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
    }
    
    
 // OrderServiceImpl.java
    @Transactional
    public void confirmPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!"PENDING".equals(order.getPaymentStatus())) {
            throw new IllegalStateException("Payment already processed");
        }

        // ✅ 1. Verify all items are in stock
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            InventoryStock stock = inventoryStockRepository.findByItemId(item.getItemId())
                    .orElseThrow(() -> new RuntimeException("Stock not found for item"));
            
            if (stock.getCurrentQuantity() < item.getQuantity()) {
                // ❌ Not enough stock → mark as partially fulfillable
                order.setOrderStatus("PARTIALLY_FULFILLABLE");
                order.setPaymentStatus("PAID");
                orderRepository.save(order);
                throw new IllegalStateException("Insufficient stock for item: " + item.getItemId());
            }
        }

        // ✅ 2. All items available → CONFIRMED
        order.setOrderStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        orderRepository.save(order);

        // ✅ 3. Reduce stock (only after payment + availability confirmed)
        for (OrderItem item : items) {
            inventoryStockRepository.adjustStockByDelta(item.getItemId(), -item.getQuantity());
            // Record transaction...
        }
    }
   
    @Transactional
    public void shipOrder(Long orderId, String updatedBy) {
        Order order = findById(orderId);

        if (!"CONFIRMED".equals(order.getOrderStatus())) {
            throw new IllegalStateException("Order must be CONFIRMED to ship");
        }

        // ✅ Generate shipment ID automatically
        String shipmentId = generateShipmentId();

        Shipment shipment = new Shipment();
        shipment.setShipmentId(shipmentId);
        shipment.setOrder(order);
        shipment.setShippedAt(LocalDateTime.now());
        shipment.setStatus("SHIPPED");
        shipment.setCreatedBy(updatedBy);
        shipment.setCreatedAt(LocalDateTime.now());

        shipmentRepository.save(shipment);

        order.setOrderStatus("SHIPPED");
        order.setUpdatedBy(updatedBy);
        order.setUpdatedAt(LocalDateTime.now());
        save(order);
    }

    // Helper: Generate SHP-YYYYMMDD-NNN
    private String generateShipmentId() {
        LocalDate today = LocalDate.now();
        String datePart = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // Get next sequence number (simplified — use DB sequence or Redis in prod)
        long nextSeq = shipmentRepository.count() + 1;
        
        return "SHP-" + datePart + "-" + String.format("%04d", nextSeq);
    }

    @Override
    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    @Override
    public Order save(Order order) {
        return orderRepository.save(order);
    }
    @Override
    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByOrderStatus(status);
    }
    
    
    @Override
    public List<OrderVerificationDTO> getOrdersForVerification(String status) {
        List<Order> orders = orderRepository.findByOrderStatus(status);
        return orders.stream().map(order -> {
            OrderVerificationDTO dto = new OrderVerificationDTO();
            dto.setOrderId(order.getOrderId());
            dto.setCustomerName(order.getCustomerName());
            dto.setCustomerEmail(order.getCustomerEmail());
            dto.setOrderStatus(order.getOrderStatus());
            dto.setTotalAmount(order.getTotalAmount());
            dto.setCreatedAt(order.getCreatedAt());
            dto.setNote(order.getNote());

            Payment payment = paymentService.findByOrderId(order.getOrderId());
            if (payment != null) {
                dto.setPaymentMethod(payment.getPaymentMethod());
                dto.setJournalNumber(payment.getJournalNumber());
                dto.setPaymentAmount(payment.getAmount());
            }

            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updatePaymentStatus(Long orderId, String paymentStatus) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        order.setPaymentStatus(paymentStatus);
        orderRepository.save(order);
    }
}