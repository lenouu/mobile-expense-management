package com.expensemanagement.backend.CaptureManagement.Config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class ReceiptCaptureConfiguration
{
    @Bean
    public MultipartConfigElement multipartConfigElement(
            @Value("${spring.servlet.multipart.max-file-size:5MB}") String maxFileSize,
            @Value("${spring.servlet.multipart.max-request-size:6MB}") String maxRequestSize)
    {
        long requestBytes = DataSize.parse(maxRequestSize).toBytes();
        // Keep receipt parts in memory rather than spooling their images to disk.
        return new MultipartConfigElement("", DataSize.parse(maxFileSize).toBytes(),
                requestBytes, Math.toIntExact(requestBytes));
    }
}
