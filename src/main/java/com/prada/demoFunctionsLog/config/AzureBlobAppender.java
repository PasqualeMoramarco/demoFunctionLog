package com.prada.demoFunctionsLog.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import com.azure.storage.blob.specialized.AppendBlobClient;
import com.azure.storage.blob.specialized.SpecializedBlobClientBuilder;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Setter
@Component
public class AzureBlobAppender extends AppenderBase<ILoggingEvent> {
    private String connectionString;
    private String containerName;
    private AppendBlobClient blobContainerClient;
    private String componentName;
    private int maxSize;
    private int currentSize = 0;
    private String blobName;
    private ExecutorService executorService;
    private Encoder<ILoggingEvent> encoder;


    @Override
    public void start() {
        super.start();
        blobContainerClient = new SpecializedBlobClientBuilder()
                .connectionString(connectionString)
                .containerName(containerName)
                .blobName(blobName + System.currentTimeMillis() + ".json")
                .buildAppendBlobClient();
        blobContainerClient.createIfNotExists();
        this.executorService = Executors.newSingleThreadExecutor();

        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        byte[] logMessage = encode(event);

        if (currentSize + logMessage.length > maxSize) {
            rollLogFile();
        }
        executorService.submit(() -> {
            try {
                uploadLog(logMessage);
            } catch (Exception e) {
                addError("Error writing log to Azure Blob Storage", e);
            }
        });

    }

    private byte[] encode(ILoggingEvent event) {
        try {
            return encoder.encode(event);
        } catch (Exception e) {
            addError("Failed to encode log event", e);
            return "{}".getBytes();
        }
    }

    private void rollLogFile() {
        currentSize = 0;
        blobContainerClient = new SpecializedBlobClientBuilder()
                .connectionString(connectionString)
                .containerName(containerName)
                .blobName(blobName + System.currentTimeMillis() + ".json")
                .buildAppendBlobClient();
        blobContainerClient.createIfNotExists();
    }

    private void uploadLog(byte[] logBytes) {
        try {
            blobContainerClient.appendBlock(new ByteArrayInputStream(logBytes), logBytes.length);
            currentSize += logBytes.length;
        } catch (Exception e) {
            addError("Failed to upload log to Azure Blob Storage", e);
        }
    }
}
