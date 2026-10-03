package com.api.inventory.service.impl;

import com.api.inventory.repository.TransactionRepository;
import com.api.inventory.dto.PaymentRequestDTO;
import com.api.inventory.entity.Payment;
import com.api.inventory.entity.Transaction;
import com.api.inventory.entity.Order;
import com.api.inventory.exception.ResourceNotFoundException;
import com.api.inventory.repository.OrderRepository;
import com.api.inventory.repository.PaymentRepository;
import com.api.inventory.service.PaymentService;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
	@Autowired
    private PaymentRepository paymentRepository;
	@Autowired
    private OrderRepository orderRepository;
	@Autowired
	private TransactionRepository transactionRepository;
	@Autowired
	private com.api.inventory.service.NotificationService notify;
	/**
	 * The customer says "I am paying for this order". The SERVER decides the amount (the order's real total)
	 * and the status ("pending" until staff check it). Amount, status and transactionId sent by the browser are ignored.
	 * Pressing Pay twice gives back the same pending payment; a processed or rejected payment cannot be created again.
	 */
	@Override
	public Payment createPayment(PaymentRequestDTO dto) {
	    Order order = orderRepository.findById(dto.getOrderId())
	            .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + dto.getOrderId()));

	    if ("CANCELLED".equalsIgnoreCase(order.getOrderStatus())) {
	        throw new IllegalStateException("This order was cancelled and cannot be paid.");
	    }

	    // Online orders are paid before delivery (riders never carry cash), so only bank transfer is accepted
	    String method = dto.getPaymentMethod() == null ? "" : dto.getPaymentMethod().trim().toLowerCase();
	    if (!"bank".equals(method)) {
	        throw new IllegalStateException("Please pay by bank transfer. Orders are delivered after the payment is checked.");
	    }
	    if (dto.getJournalNumber() == null || dto.getJournalNumber().isBlank()) {
	        throw new IllegalStateException("Enter the journal number from your bank receipt.");
	    }

	    Optional<Payment> existing = paymentRepository.findByOrderId(order.getOrderId());
	    if (existing.isPresent()) {
	        if ("pending".equalsIgnoreCase(existing.get().getStatus())) {
	            return existing.get();
	        }
	        throw new IllegalStateException("A payment for this order was already processed.");
	    }

	    BigDecimal total = order.getTotalAmount();
	    if (total == null || total.signum() <= 0) {
	        throw new IllegalStateException("This order has no amount to pay.");
	    }

	    Payment payment = new Payment();
	    payment.setOrderId(order.getOrderId());
	    payment.setPaymentMethod(dto.getPaymentMethod());
	    payment.setAmount(total);
	    payment.setStatus("pending");
	    payment.setPaymentDate(LocalDateTime.now());
	    payment.setJournalNumber(dto.getJournalNumber());

	    Payment saved = paymentRepository.save(payment);
	    notify.withPermission("payments.verify", new com.api.inventory.service.NotificationService.Note("PAYMENT_TO_CHECK",
	            "Payment to check: order #" + order.getOrderId(),
	            "Nu. " + total + " by bank transfer, journal number " + dto.getJournalNumber().trim() + ".", "/order-verification"), false);
	    return saved;
	}

    @Override
    public Payment updatePaymentStatus(Long paymentId, String status) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with ID: " + paymentId));

        payment.setStatus(status);
        payment.setPaymentDate(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    @Override
    public Payment getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order ID: " + orderId));
    }

    @Override
    public Payment findByOrderId(Long orderId) {
        System.out.println("Searching for payment with order ID: " + orderId);
        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(orderId);
        if (paymentOpt.isPresent()) {
            System.out.println("Found payment: " + paymentOpt.get());
            return paymentOpt.get();
        } else {
            System.out.println("No payment found for order ID: " + orderId);
            return null;
        }
    }

    @Override
    public Payment save(Payment payment) {
        return paymentRepository.save(payment);
    }

    @Override
    public Payment findById(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new RuntimeException("Payment not found"));
    }
}