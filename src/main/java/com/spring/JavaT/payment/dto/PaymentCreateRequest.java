package com.spring.JavaT.payment.dto;

import com.spring.JavaT.common.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class PaymentCreateRequest {

    @NotNull
    private Long billId;

    @NotNull
    @Positive
    private BigDecimal amountPaid;

    @NotNull
    private PaymentMethod paymentMethod;

    private LocalDate paymentDate;
}
