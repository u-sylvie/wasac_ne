package com.spring.JavaT.bill.dto;

import com.spring.JavaT.common.BillStatus;
import com.spring.JavaT.common.EntityStatus;
import com.spring.JavaT.common.MeterType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Value
@Builder
public class BillResponse {
    Long id;
    String reference;
    Long customerId;
    String customerName;
    Long meterId;
    String meterNumber;
    MeterType meterType;
    Long meterReadingId;
    Long tariffConfigId;
    Integer tariffVersion;
    Integer billingYear;
    Integer billingMonth;
    BigDecimal consumption;
    BigDecimal consumptionCharge;
    BigDecimal fixedCharge;
    BigDecimal taxAmount;
    BigDecimal penaltyAmount;
    BigDecimal totalAmount;
    BigDecimal amountPaid;
    BigDecimal outstandingBalance;
    LocalDate dueDate;
    BillStatus billStatus;
    Instant approvedAt;
    String approvedBy;
    EntityStatus status;
}
