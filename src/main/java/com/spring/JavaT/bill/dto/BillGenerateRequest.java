package com.spring.JavaT.bill.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BillGenerateRequest {

    @NotNull
    private Long meterReadingId;

    private LocalDate dueDate;
}
