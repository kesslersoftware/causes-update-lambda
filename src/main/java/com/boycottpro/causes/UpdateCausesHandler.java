package com.boycottpro.causes;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.boycottpro.models.Causes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class UpdateCausesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = "";
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpdateCausesHandler() {
        this.dynamoDb = DynamoDbClient.create();
    }

    public UpdateCausesHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            Causes input = objectMapper.readValue(event.getBody(), Causes.class);
            boolean validInput = input.getCause_desc()!=null && !input.getCause_desc().isEmpty() &&
                    input.getFollower_count()>=0;
            if(!validInput) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("{\"error\":\"Invalid input\"}");
            }
            boolean updatedCause = false;
            boolean insert = false;
            if(input.getCause_id()==null) {
                insert = true;
            }
            if(insert) {
                updatedCause = insertCause(input);
            } else {
                updatedCause = updateCause(input);
            }

            if(!updatedCause) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(500)
                        .withBody("{\"error\": \"failed to update the cause\"}");
            }
            String responseBody = objectMapper.writeValueAsString(input);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(responseBody);
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Unexpected server error: " + e.getMessage() + "\"}");
        }
    }
    private boolean insertCause(Causes cause) {
        // Generate a new UUID for the cause_id
        String causeId = UUID.randomUUID().toString();
        cause.setCause_id(causeId);

        // Build the item map
        Map<String, AttributeValue> item = Map.ofEntries(
                Map.entry("cause_id", AttributeValue.fromS(causeId)),
                Map.entry("category", AttributeValue.fromS(cause.getCategory())),
                Map.entry("cause_desc", AttributeValue.fromS(cause.getCause_desc())),
                Map.entry("follower_count", AttributeValue.fromN(Integer.toString(cause.getFollower_count())))
        );

        // Construct the PutItemRequest
        PutItemRequest putRequest = PutItemRequest.builder()
                .tableName("causes")
                .item(item)
                .build();

        // Execute the insert
        dynamoDb.putItem(putRequest);
        return true;
    }

    private boolean updateCause(Causes cause) {
        String causeId = cause.getCause_id();

        // Step 1: Check if the cause exists
        GetItemRequest getRequest = GetItemRequest.builder()
                .tableName("causes")
                .key(Map.of("cause_id", AttributeValue.fromS(causeId)))
                .build();

        GetItemResponse getResponse = dynamoDb.getItem(getRequest);
        if (!getResponse.hasItem()) {
            return false; // Cause not found
        }

        // Step 2: Build the UpdateItemRequest
        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("causes")
                .key(Map.of("cause_id", AttributeValue.fromS(causeId)))
                .updateExpression("SET " +
                        "cause_desc = :desc, category = :cat, follower_count = :fol")
                .expressionAttributeValues(Map.ofEntries(
                        Map.entry(":cat", AttributeValue.fromS(cause.getCategory())),
                        Map.entry(":desc", AttributeValue.fromS(cause.getCause_desc())),
                        Map.entry(":fol", AttributeValue.fromN(Long.toString(cause.getFollower_count())))
                ))
                .build();

        // Step 3: Execute the update
        dynamoDb.updateItem(updateRequest);
        return true;
    }
}