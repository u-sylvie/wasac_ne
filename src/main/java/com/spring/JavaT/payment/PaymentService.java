package com.spring.JavaT.payment;

import com.spring.JavaT.audit.AuditService;
import com.spring.JavaT.common.SecurityHelper;
import com.spring.JavaT.common.filter.BaseSpecification;
import com.spring.JavaT.common.filter.SearchCriteria;
import com.spring.JavaT.bill.BillRepository;
import com.spring.JavaT.exception.BusinessException;
import com.spring.JavaT.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import com.spring.JavaT.notification.NotificationService;
import com.spring.JavaT.payment.dto.PaymentCreateRequest;
import com.spring.JavaT.payment.dto.PaymentResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final EntityManager entityManager;
    private final AuditService auditService;
    private final SecurityHelper securityHelper;
    private final NotificationService notificationService;

    public Page<PaymentResponse> getAll(List<SearchCriteria> criteria, Pageable pageable) {
        Specification<Payment> spec = new BaseSpecification<>(criteria);
        return paymentRepository.findAll(spec, pageable).map(PaymentMapper::toResponse);
    }

    public Page<PaymentResponse> getAllForCustomer(Long customerId, List<SearchCriteria> criteria, Pageable pageable) {
        List<SearchCriteria> allCriteria = new java.util.ArrayList<>(criteria);
        allCriteria.add(new SearchCriteria("bill.customer.id", SearchCriteria.Op.EQ, customerId));
        return getAll(allCriteria, pageable);
    }

    public PaymentResponse getById(Long id) {
        return PaymentMapper.toResponse(findOrThrow(id));
    }

    public PaymentResponse getByIdForCustomer(Long id, String userEmail) {
        Payment payment = findOrThrow(id);
        securityHelper.ensureCustomerOwns(payment.getBill().getCustomer().getId(), userEmail);
        return PaymentMapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse recordPayment(PaymentCreateRequest request, String actor) {
        LocalDate paymentDate = request.getPaymentDate() != null
                ? request.getPaymentDate()
                : LocalDate.now();

        if (paymentDate.isAfter(LocalDate.now())) {
            throw new BusinessException("Payment date cannot be in the future", HttpStatus.BAD_REQUEST);
        }
        if (request.getAmountPaid().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be greater than zero", HttpStatus.BAD_REQUEST);
        }

        var bill = billRepository.findById(request.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill", "id", request.getBillId()));
        if (request.getAmountPaid().compareTo(bill.getOutstandingBalance()) > 0) {
            throw new BusinessException(
                    "Payment amount exceeds outstanding balance of " + bill.getOutstandingBalance(),
                    HttpStatus.BAD_REQUEST);
        }

        StoredProcedureQuery query = entityManager.createStoredProcedureQuery("sp_record_payment");
        query.registerStoredProcedureParameter("p_bill_id", Long.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_amount_paid", java.math.BigDecimal.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_payment_method", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_payment_date", LocalDate.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_payment_id", Long.class, ParameterMode.OUT);

        query.setParameter("p_bill_id", request.getBillId());
        query.setParameter("p_amount_paid", request.getAmountPaid());
        query.setParameter("p_payment_method", request.getPaymentMethod().name());
        query.setParameter("p_payment_date", paymentDate);

        query.execute();
        Long paymentId = (Long) query.getOutputParameterValue("p_payment_id");

        Payment payment = findOrThrow(paymentId);
        auditService.log("Payment", payment.getId(), "CREATE", actor,
                "Recorded payment of " + payment.getAmountPaid() + " for bill " + payment.getBill().getReference());
        log.info("Payment {} recorded for bill {}", payment.getReference(), payment.getBill().getReference());

        // sp_record_payment may have queued PAYMENT_COMPLETED — dispatch emails now
        notificationService.sendPendingEmails(actor);

        return PaymentMapper.toResponse(payment);
    }

    private Payment findOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id));
    }
}
