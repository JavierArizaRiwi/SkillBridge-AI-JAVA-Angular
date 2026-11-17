package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.CancelBookingCommand;
import com.riwi.skillbridge.application.port.in.CancelBookingUseCase;
import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.ListCustomerBookingsUseCase;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateBookingRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final CreateBookingUseCase useCase;
    private final ListCustomerBookingsUseCase listCustomerBookingsUseCase;
    private final CancelBookingUseCase cancelBookingUseCase;

    public BookingController(CreateBookingUseCase useCase,
                             ListCustomerBookingsUseCase listCustomerBookingsUseCase,
                             CancelBookingUseCase cancelBookingUseCase) {
        this.useCase = useCase;
        this.listCustomerBookingsUseCase = listCustomerBookingsUseCase;
        this.cancelBookingUseCase = cancelBookingUseCase;
    }

    // Permitir a CUSTOMER, PROVIDER y ADMIN crear reservas
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Booking create(@Valid @RequestBody CreateBookingRequest request, Authentication authentication,
                          @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return useCase.create(request.offeringId(), request.scheduledAt(), authentication.getName(), idempotencyKey);
    }

    // Si deseas que PROVIDER o ADMIN también puedan listar sus reservas o consultar esta ruta:
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')")
    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public PageResponse<Booking> getBookingsById(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(defaultValue = "scheduledAt,desc") String sort,
                                                 Authentication authentication) {
        String userEmail = authentication.getName();
        return PageResponse.from(
            listCustomerBookingsUseCase.bookingsList(userEmail, PageParams.toQuery(page, size, sort)),
            booking -> booking);
    }

    @PatchMapping("/{bookingId}/cancel")
    public Booking cancel(@PathVariable java.util.UUID bookingId, Authentication authentication) {
        return cancelBookingUseCase.cancel(new CancelBookingCommand(bookingId, authentication.getName()));
    }
}
