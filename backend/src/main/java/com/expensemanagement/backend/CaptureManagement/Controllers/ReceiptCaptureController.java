package com.expensemanagement.backend.CaptureManagement.Controllers;

import com.expensemanagement.backend.CaptureManagement.Dtos.DtoResponses.CaptureResponses;
import com.expensemanagement.backend.CaptureManagement.Services.ServiceImplements.ReceiptCaptureService;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping(ReceiptCaptureController.BASE_PATH)
@PreAuthorize("hasRole('USER')")
public class ReceiptCaptureController
{
    public static final String BASE_PATH = "/api/v1/captures/receipts";
    private final ReceiptCaptureService receiptCaptureService;

    public ReceiptCaptureController(ReceiptCaptureService receiptCaptureService)
    {
        this.receiptCaptureService = receiptCaptureService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CaptureResponses.Receipt capture(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file)
    {
        if (file.getSize() > ReceiptCaptureService.MAX_UPLOAD_BYTES)
        {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Receipt images must be 5 MB or smaller");
        }
        try
        {
            return receiptCaptureService.capture(UUID.fromString(jwt.getSubject()), file.getBytes(), file.getContentType());
        }
        catch (IOException ex)
        {
            throw new InvalidRequestException("Receipt image could not be read");
        }
    }
}
