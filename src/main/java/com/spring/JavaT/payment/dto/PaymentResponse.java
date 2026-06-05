package com.spring.JavaT.payment.dto;

import com.spring.JavaT.common.EntityStatus;
import com.spring.JavaT.common.PaymentMethod;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class PaymentResponse {
    Long id;
    Long billId;
    String billReference;
    BigDecimal amountPaid;
    PaymentMethod paymentMethod;
    LocalDate paymentDate;
    String reference;
    EntityStatus status;
}
