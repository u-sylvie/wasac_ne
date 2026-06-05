package com.spring.JavaT.bill;

import com.spring.JavaT.bill.dto.BillResponse;

public final class BillMapper {

    private BillMapper() {}

    public static BillResponse toResponse(Bill bill) {
        return BillResponse.builder()
                .id(bill.getId())
                .reference(bill.getReference())
                .customerId(bill.getCustomer().getId())
                .customerName(bill.getCustomer().getFullName())
                .meterId(bill.getMeter().getId())
                .meterNumber(bill.getMeter().getMeterNumber())
                .meterType(bill.getMeter().getMeterType())
                .meterReadingId(bill.getMeterReading().getId())
                .tariffConfigId(bill.getTariffConfig().getId())
                .tariffVersion(bill.getTariffConfig().getVersion())
                .billingYear(bill.getBillingYear())
                .billingMonth(bill.getBillingMonth())
                .consumption(bill.getConsumption())
                .consumptionCharge(bill.getConsumptionCharge())
                .fixedCharge(bill.getFixedCharge())
                .taxAmount(bill.getTaxAmount())
                .penaltyAmount(bill.getPenaltyAmount())
                .totalAmount(bill.getTotalAmount())
                .amountPaid(bill.getAmountPaid())
                .outstandingBalance(bill.getOutstandingBalance())
                .dueDate(bill.getDueDate())
                .billStatus(bill.getBillStatus())
                .approvedAt(bill.getApprovedAt())
                .approvedBy(bill.getApprovedBy())
                .status(bill.getStatus())
                .build();
    }
}
