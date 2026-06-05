package com.spring.JavaT.payment;

import com.spring.JavaT.payment.dto.PaymentResponse;

public final class PaymentMapper {

    private PaymentMapper() {}

    public static PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .billId(payment.getBill().getId())
                .billReference(payment.getBill().getReference())
                .amountPaid(payment.getAmountPaid())
                .paymentMethod(payment.getPaymentMethod())
                .paymentDate(payment.getPaymentDate())
                .reference(payment.getReference())
                .status(payment.getStatus())
                .build();
    }
}
