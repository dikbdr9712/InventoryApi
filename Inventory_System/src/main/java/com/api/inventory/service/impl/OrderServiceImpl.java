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
import com.api.inventory.service.PackageService;
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
	@Autowired
	private SellerProfileRepository sellerProfileRepository;
	@Autowired
	private PackageService packageService;
	@Autowired
	private com.api.inventory.service.PosShiftService posShiftService;
	@Autowired
	private com.api.inventory.service.CustomerService customerService;
	@Autowired
	private com.api.inventory.service.DeliveryPricingService deliveryPricing;
	@Autowired
	private com.api.inventory.service.NotificationService notify;
	@Autowired
	private com.api.inventory.service.StockService stockService;
	@Autowired
	private com.api.inventory.service.JournalNumbers journalNumbers;


	private static final Set<String> POS_METHODS = Set.of("CASH", "CARD", "UPI", "BANK_TRANSFER");
	private static final Set<String> TAX_TYPES = Set.of("GST", "ET", "CDA", "VAT", "OTHER");
	private static final BigDecimal MAX_TAX_RATE = new BigDecimal("50");
	/** Rounding room for discounts sent with 8 decimals. */
	private static final BigDecimal DISCOUNT_TOLERANCE = new BigDecimal("0.0001");

	@Transactional
	public Order createInPersonSale(PosSaleRequestDTO request) {
	    if (request.getItems() == null || request.getItems().isEmpty()) {
	        throw new IllegalArgumentException("At least one item is required");
	    }

	    // The same sale sent twice (double click, network retry) is saved once: give back the first one
	    String clientRef = request.getClientRef() == null ? null : request.getClientRef().trim();
	    if (clientRef != null && !clientRef.isEmpty()) {
	        if (clientRef.length() > 64) {
	            throw new IllegalArgumentException("Sale reference is too long.");
	        }
	        java.util.Optional<Order> already = orderRepository.findByClientRef(clientRef);
	        if (already.isPresent()) {
	            return already.get();
	        }
	    } else {
	        clientRef = null;
	    }

	    // No open cash drawer, no sale: every sale belongs to a cashier's shift
	    PosShift shift = posShiftService.requireOpenShift();

	    String method = request.getPaymentMethod() == null ? "" : request.getPaymentMethod().trim().toUpperCase();
	    if (!POS_METHODS.contains(method)) {
	        throw new IllegalArgumentException("Choose how the customer paid.");
	    }
	    // Paid without cash: the journal number (bank transfer / mobile banking) is required, so the money can be
	    // found on the bank statement; card approval codes and UPI numbers are kept when typed. Never one used before.
	    String paymentReference = null;
	    if ("BANK_TRANSFER".equals(method)) {
	        paymentReference = journalNumbers.requireNew(request.getPaymentReference(), "journal number from the customer's banking app");
	    } else if (!"CASH".equals(method) && com.api.inventory.service.JournalNumbers.tidy(request.getPaymentReference()) != null) {
	        paymentReference = journalNumbers.requireNew(request.getPaymentReference(),
	                "CARD".equals(method) ? "card approval code" : "transaction number");
	    }

	    String phone = request.getCustomerPhone() == null ? null : request.getCustomerPhone().trim();
	    if (phone != null && !phone.isEmpty() && !phone.matches("^[0-9]{8}$")) {
	        throw new IllegalArgumentException("Enter an 8-digit phone number, or leave it empty.");
	    }

	    // Taxes: only known types, sensible rates, each type once
	    if (request.getTaxes() != null) {
	        Set<String> seen = new java.util.HashSet<>();
	        for (TaxInfoDTO t : request.getTaxes()) {
	            String type = t.getType() == null ? "" : t.getType().trim().toUpperCase();
	            double rate = t.getRate() == null ? 0 : t.getRate();
	            if (!TAX_TYPES.contains(type) || rate < 0 || BigDecimal.valueOf(rate).compareTo(MAX_TAX_RATE) > 0) {
	                throw new IllegalArgumentException("Check the tax: " + t.getType() + " at " + t.getRate() + "%.");
	            }
	            if (!seen.add(type)) {
	                throw new IllegalArgumentException(type + " is added twice.");
	            }
	        }
	    }

	    // Discounts: the shop's own price is always allowed. More than that needs "Give extra discounts",
	    // and never more than the product's maximum discount.
	    boolean mayDiscount = com.api.inventory.security.CurrentUser.has("pos.discount");
	    for (ItemQty item : request.getItems()) {
	        if (item.getItemId() == null || item.getQuantity() == null) {
	            throw new IllegalArgumentException("A line in the sale is incomplete.");
	        }
	        ItemMaster m = itemMasterRepository.findById(item.getItemId())
	                .orElseThrow(() -> new IllegalArgumentException("A product in the sale no longer exists. Reload the products."));
	        BigDecimal asked = item.getDiscountPercent() == null ? BigDecimal.ZERO : item.getDiscountPercent();
	        if (asked.signum() < 0 || asked.compareTo(BigDecimal.valueOf(100)) > 0) {
	            throw new IllegalArgumentException("The discount on " + m.getItemName() + " must be between 0 and 100%.");
	        }
	        BigDecimal shopDiscount = BigDecimal.ZERO;
	        if (m.getMrp() != null && m.getMrp().signum() > 0 && m.getSellingPrice() != null && m.getSellingPrice().compareTo(m.getMrp()) < 0) {
	            shopDiscount = m.getMrp().subtract(m.getSellingPrice()).multiply(BigDecimal.valueOf(100)).divide(m.getMrp(), 8, RoundingMode.HALF_UP);
	        }
	        BigDecimal limit = shopDiscount;
	        if (mayDiscount && !Boolean.FALSE.equals(m.getDiscountAllowed())) {
	            BigDecimal max = m.getMaxDiscountPercent() == null ? BigDecimal.valueOf(100) : m.getMaxDiscountPercent();
	            limit = max.max(shopDiscount);
	        }
	        if (asked.compareTo(limit.add(DISCOUNT_TOLERANCE)) > 0) {
	            throw new IllegalStateException(mayDiscount
	                    ? "The most you can take off " + m.getItemName() + " is " + limit.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%."
	                    : "You are not allowed to give an extra discount on " + m.getItemName() + ". Ask a manager.");
	        }
	    }

	    BigDecimal subtotalBeforeDiscount = BigDecimal.ZERO; // MRP-based total
	    BigDecimal totalAfterDiscount = BigDecimal.ZERO;     // Final price total
	    BigDecimal totalDiscountAmount = BigDecimal.ZERO;   // ← What we'll save
	    List<OrderItem> orderItemsToSave = new ArrayList<>();

	    for (ItemQty item : request.getItems()) {
	        if (item.getQuantity() < 1) {
	            throw new IllegalArgumentException("Quantity must be at least 1");
	        }
	        ItemMaster master = itemMasterRepository.findById(item.getItemId())
	                .orElseThrow(() -> new RuntimeException("Item not found: " + item.getItemId()));
	        if (master.getSellerId() != null) {
	            throw new IllegalStateException(master.getItemName() + " belongs to a marketplace seller and is sold online only.");
	        }

	        InventoryStock stock = inventoryStockRepository.findByItemId(item.getItemId())
	                .orElseThrow(() -> new RuntimeException("Stock not initialized"));
	        if (stock.getCurrentQuantity() < item.getQuantity()) {
	            throw new IllegalStateException("Insufficient stock for item: " + master.getItemName());
	        }

	        // The shop's price list decides the price, not the browser. A different MRP means the screen is out of date (or was changed).
	        if (item.getMrp() != null && item.getMrp().compareTo(BigDecimal.ZERO) > 0
	                && (master.getMrp() == null || item.getMrp().compareTo(master.getMrp()) != 0)) {
	            throw new IllegalStateException("The price of " + master.getItemName() + " has changed. Please reload the products and try again.");
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

	    BigDecimal tendered = request.getAmountTendered();
	    if ("CASH".equals(method) && tendered != null
	            && tendered.compareTo(finalTotal.setScale(2, RoundingMode.HALF_UP)) < 0) {
	        throw new IllegalStateException("Cash received (Nu. " + tendered + ") is less than the total (Nu. "
	                + finalTotal.setScale(2, RoundingMode.HALF_UP) + ").");
	    }

	    // Save ORDER
	    Order order = new Order();
	    order.setCustomerName(request.getCustomerName());
	    order.setCustomerPhone(phone == null || phone.isEmpty() ? null : phone);
	    order.setPaymentMethod(method);
	    order.setShiftId(shift.getId());
	    order.setCashier(shift.getCashierEmail());
	    order.setUpdatedBy(shift.getCashierEmail());
	    order.setClientRef(clientRef);
	    order.setAmountTendered("CASH".equals(method) ? tendered : null);
	    order.setPaymentReference(paymentReference);
	    order.setOrderStatus("COMPLETED");
	    order.setPaymentStatus("PAID");
	    order.setSource("POS");
	    order.setTotalAmount(finalTotal);
	    order.setDiscountAmount(totalDiscountAmount); // ✅ Now stores real discount!
	    order.setTaxAmount(totalTax);
	    order.setCreatedAt(LocalDateTime.now());
	    order.setUpdatedAt(LocalDateTime.now());

	    Order savedOrder = orderRepository.save(order);
	    customerService.linkOrder(savedOrder); // only when the customer gave a phone number

	    // Save tax details
	    for (TaxDetail detail : taxDetailsToSave) {
	        detail.setOrder(savedOrder);
	    }
	    taxDetailRepository.saveAll(taxDetailsToSave);

	    // Save order items, deduct stock, create transactions
	    for (OrderItem orderItem : orderItemsToSave) {
	        orderItem.setOrderId(savedOrder.getOrderId());
	        orderItemRepository.save(orderItem);

	        takeStock(orderItem);

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

	    if (dto.getAddress() == null || dto.getAddress().isBlank()) {
	        throw new IllegalStateException("Please enter the delivery address.");
	    }

	    for (OrderRequestDTO.Item item : dto.getNormalizedItems()) {
	        if (item.getQuantity() < 1) {
	            throw new IllegalArgumentException("Quantity must be at least 1 (item " + item.getItemId() + ")");
	        }
	        ItemMaster itemMaster = itemMasterRepository.findById(item.getItemId())
	            .orElseThrow(() -> new IllegalStateException("A product in your cart is no longer sold. Please remove it."));

	        if (Boolean.FALSE.equals(itemMaster.getIsActive())) {
	            throw new IllegalStateException(itemMaster.getItemName() + " is no longer sold. Please remove it from your cart.");
	        }
	        if (itemMaster.getSellerId() != null
	                && !sellerProfileRepository.findById(itemMaster.getSellerId()).map(SellerProfile::isApproved).orElse(false)) {
	            throw new IllegalStateException(itemMaster.getItemName() + " is not available right now. Please remove it from your cart.");
	        }
	        int inStock = inventoryStockRepository.findByItemId(item.getItemId()).map(InventoryStock::getCurrentQuantity).orElse(0);
	        if (inStock < item.getQuantity()) {
	            throw new IllegalStateException("Only " + Math.max(inStock, 0) + " of " + itemMaster.getItemName() + " left. Please change the quantity.");
	        }

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
	    // where on the map: the chosen delivery area, or the phone's location (none = the delivery fee is estimated)
	    com.api.inventory.service.DeliveryPricingService.Drop drop =
	            deliveryPricing.drop(dto.getDropLatitude(), dto.getDropLongitude(), dto.getAreaId());
	    if (drop.point() != null) {
	        order.setDropLatitude(drop.point().latitude());
	        order.setDropLongitude(drop.point().longitude());
	        order.setDropLocation(drop.label());
	    }
	    order.setOrderStatus("CREATED");
	    order.setPaymentStatus("PENDING");
	    order.setTotalAmount(totalAmount);
	    order.setCreatedAt(LocalDateTime.now());
	    order.setUpdatedAt(LocalDateTime.now());
	    order.setSource("ONLINE");

	    if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
	        throw new IllegalArgumentException("Your order total must be more than zero.");
	    }
	    
	    Order savedOrder = orderRepository.save(order);

	    for (OrderItem item : orderItems) {
	        item.setOrderId(savedOrder.getOrderId());
	    }

	    // One package per seller; the delivery fees are added to what the customer pays
	    BigDecimal deliveryFees = packageService.createPackages(savedOrder, orderItems);
	    orderItemRepository.saveAll(orderItems);

	    savedOrder.setDeliveryFee(deliveryFees);
	    savedOrder.setTotalAmount(totalAmount.add(deliveryFees));
	    Order finalOrder = orderRepository.save(savedOrder);
	    customerService.linkOrder(finalOrder); // the buyer's customer record and history
	    notify.customer(finalOrder, new com.api.inventory.service.NotificationService.Note("ORDER_PLACED",
	            "Order #" + finalOrder.getOrderId() + " received",
	            "Total Nu. " + finalOrder.getTotalAmount() + ". We start packing as soon as your payment is confirmed.",
	            "/orders/" + finalOrder.getOrderId()), true, null);
	    return finalOrder;
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
	        takeStock(item);

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
	    packageService.markOrderPaid(orderId); // sellers can start packing
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
            takeStock(item);

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
        packageService.markOrderPaid(orderId); // sellers can start packing
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

        if ("SHIPPED".equals(order.getOrderStatus()) || "COMPLETED".equals(order.getOrderStatus())) {
            throw new IllegalStateException("This order has already left the shop and can no longer be cancelled. Use Return to take items back.");
        }

        if ("CANCELLED".equals(order.getOrderStatus())) {
            return;
        }

        packageService.cancelForOrder(orderId); // refuses if a package is already on the way

        if ("CONFIRMED".equals(order.getOrderStatus())) {
            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : items) {
                stockService.putBack(item.getOrderItemId(), item.getItemId(), item.getQuantity(), item.getUnitCost());
                
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

        boolean paid = "PAID".equals(order.getPaymentStatus());
        order.setOrderStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        if ("ONLINE".equals(order.getSource())) {
            notify.customer(order, new com.api.inventory.service.NotificationService.Note("ORDER_CANCELLED",
                    "Order #" + orderId + " was cancelled",
                    paid ? "You had already paid: we will contact you about the refund." : "Nothing was charged.",
                    "/orders/" + orderId), true, null);
        }
    }

    @Override
    @Transactional
    public void completeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found"));

        if (packageService.hasPackages(orderId)) {
            throw new IllegalStateException("This order is completed automatically when every package is delivered. See Deliveries.");
        }

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
        order.setUpdatedBy(com.api.inventory.security.CurrentUser.email());
        if (order.getPaymentVerifiedBy() == null) {
            order.setPaymentVerifiedBy(com.api.inventory.security.CurrentUser.email()); // an online payment sets its own first
        }
        order.setPaymentVerifiedAt(java.time.Instant.now());
        orderRepository.save(order);

        // the payment the customer sent is now checked, too (so it no longer shows as waiting)
        Payment sent = paymentService.findByOrderId(orderId);
        if (sent != null && "pending".equalsIgnoreCase(sent.getStatus())) {
            sent.setStatus("confirmed");
            paymentService.save(sent);
        }

        // ✅ 3. Reduce stock (only after payment + availability confirmed)
        for (OrderItem item : items) {
            takeStock(item);
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
        packageService.markOrderPaid(orderId); // sellers can start packing
    }

    @Transactional
    public void shipOrder(Long orderId, String updatedBy) {
        Order order = findById(orderId);

        if (!"CONFIRMED".equals(order.getOrderStatus())) {
            throw new IllegalStateException("Order must be CONFIRMED to ship");
        }
        if (packageService.hasPackages(orderId)) {
            throw new IllegalStateException("This order goes out package by package: pack it and hand it to a rider in Deliveries.");
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
    /**
     * Takes a sold line's stock in one step, from the batch that expires first, and records its real cost.
     * Refuses (and rolls the whole sale back) if another sale took it first or only expired stock is left.
     */
    private void takeStock(OrderItem line) {
        stockService.takeForLine(line);
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