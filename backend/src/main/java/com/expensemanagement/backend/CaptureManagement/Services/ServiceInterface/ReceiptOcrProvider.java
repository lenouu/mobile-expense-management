package com.expensemanagement.backend.CaptureManagement.Services.ServiceInterface;

public interface ReceiptOcrProvider
{
    String extractText(byte[] image, String contentType);
}
