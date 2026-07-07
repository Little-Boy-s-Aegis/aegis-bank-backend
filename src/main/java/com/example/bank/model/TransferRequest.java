package com.example.bank.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;

public class TransferRequest {

    @NotBlank(message = "Source account number is required")
    private String sourceAccountNumber;

    @NotBlank(message = "Recipient account number is required")
    private String targetAccountNumber;

    @NotNull(message = "Transfer amount is required")
    @JsonDeserialize(using = StrictDoubleDeserializer.class)
    private Double amount;

    @NotBlank(message = "Description message is required")
    @Size(max = 1000, message = "Description message must not exceed 1000 characters")
    private String description;

    public TransferRequest() {}

    public TransferRequest(String sourceAccountNumber, String targetAccountNumber, Double amount, String description) {
        this.sourceAccountNumber = sourceAccountNumber;
        this.targetAccountNumber = targetAccountNumber;
        this.amount = amount;
        this.description = description;
    }

    public String getSourceAccountNumber() {
        return sourceAccountNumber;
    }

    public void setSourceAccountNumber(String sourceAccountNumber) {
        this.sourceAccountNumber = sourceAccountNumber;
    }

    public String getTargetAccountNumber() {
        return targetAccountNumber;
    }

    public void setTargetAccountNumber(String targetAccountNumber) {
        this.targetAccountNumber = targetAccountNumber;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static class StrictDoubleDeserializer extends JsonDeserializer<Double> {
        @Override
        public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.getCurrentToken() == JsonToken.VALUE_STRING) {
                throw ctxt.weirdStringException(p.getText(), Double.class, "String values are not allowed for numeric fields");
            }
            return p.getValueAsDouble();
        }
    }
}
