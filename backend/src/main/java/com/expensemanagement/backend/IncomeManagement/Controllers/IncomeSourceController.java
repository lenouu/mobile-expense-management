package com.expensemanagement.backend.IncomeManagement.Controllers;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests.IncomeSourceRequests;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses.IncomeSourceResponses;
import com.expensemanagement.backend.IncomeManagement.Services.ServiceInterface.IncomeSourceService;
import com.expensemanagement.backend.Swagger.SwaggerConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Income sources: the user's own list of where money comes from.
 *
 * <p>Deliberately separate from {@code /categories}, even though the two are edited side by side:
 * what you earn and what you spend are different vocabularies, and sharing one table would put
 * "Salary" on an expense.
 */
@RestController
@RequestMapping("/api/income-sources")
@Tag(name = "Income sources", description = "Where the caller's income comes from")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class IncomeSourceController
{
    private final IncomeSourceService sourceService;

    public IncomeSourceController(IncomeSourceService sourceService)
    {
        this.sourceService = sourceService;
    }

    @GetMapping
    @Operation(summary = "List the caller's income sources",
            description = "Alphabetical, each with how many income entries use it.")
    @ApiResponse(responseCode = "200", description = "Sources")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    public IncomeSourceResponses.Listing list(@AuthenticationPrincipal Jwt jwt)
    {
        return sourceService.listMine(currentUserId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an income source",
            description = "Names are unique per account; icon, colour and description are optional.")
    @ApiResponse(responseCode = "201", description = "Source created")
    @ApiResponse(responseCode = "409", description = "A source with that name already exists", content = @Content)
    public IncomeSourceResponses.Details create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody IncomeSourceRequests.SaveIncomeSource request)
    {
        return sourceService.create(currentUserId(jwt), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename or restyle a source")
    @ApiResponse(responseCode = "200", description = "Source updated")
    @ApiResponse(responseCode = "404", description = "Source not found", content = @Content)
    @ApiResponse(responseCode = "409", description = "Another source already has that name", content = @Content)
    public IncomeSourceResponses.Details update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody IncomeSourceRequests.SaveIncomeSource request)
    {
        return sourceService.update(currentUserId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a source",
            description = "Only while no income refers to it. A refused delete names the entries in "
                    + "the way, by name or by date.")
    @ApiResponse(responseCode = "204", description = "Source deleted")
    @ApiResponse(responseCode = "400", description = "Income entries still use it", content = @Content)
    @ApiResponse(responseCode = "404", description = "Source not found", content = @Content)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id)
    {
        sourceService.delete(currentUserId(jwt), id);
    }

    private UUID currentUserId(Jwt jwt)
    {
        return UUID.fromString(jwt.getSubject());
    }

    /** Kept for the date type import the class documents; the service owns the range logic. */
    @SuppressWarnings("unused")
    private static void describeRange(LocalDate from, LocalDate to)
    {
        if (from != null && to != null && from.isAfter(to))
        {
            throw new IllegalArgumentException("from must not be after to");
        }
    }

    /** Unused placeholder so the ISO date format import stays meaningful for future endpoints. */
    @SuppressWarnings("unused")
    private static final Class<DateTimeFormat> ISO = DateTimeFormat.class;
}
