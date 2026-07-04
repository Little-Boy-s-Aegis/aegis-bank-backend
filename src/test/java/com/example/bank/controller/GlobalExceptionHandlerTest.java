package com.example.bank.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GlobalExceptionHandlerTest {

      private GlobalExceptionHandler exceptionHandler;

      @BeforeEach
      public void setup() {
          exceptionHandler = new GlobalExceptionHandler();
      }

      @Test
      public void testHandleJsonParseException() {
          HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Malformatted JSON");
          ResponseEntity<Map<String, String>> response = exceptionHandler.handleJsonParseException(ex);

          assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
          assertNotNull(response.getBody());
          assertEquals("Malformed or unparseable JSON request body", response.getBody().get("error"));
      }

      @Test
      public void testHandleIllegalArgumentException() {
          IllegalArgumentException ex = new IllegalArgumentException("Invalid argument test");
          ResponseEntity<Map<String, String>> response = exceptionHandler.handleIllegalArgumentException(ex);

          assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
          assertNotNull(response.getBody());
          assertEquals("Invalid argument test", response.getBody().get("error"));
      }

      @Test
      public void testHandleGenericException() {
          Exception ex = new Exception("Generic exception test");
          ResponseEntity<Map<String, String>> response = exceptionHandler.handleGenericException(ex);

          assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
          assertNotNull(response.getBody());
          assertEquals("An internal server error occurred. Transaction aborted.", response.getBody().get("error"));
      }
}
