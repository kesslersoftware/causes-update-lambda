package com.boycottpro.causes;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.Causes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
public class UpdateCausesHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @InjectMocks
    private UpdateCausesHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ArgumentCaptor<PutItemRequest> putCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
    private final ArgumentCaptor<UpdateItemRequest> updateCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);

    @Test
    void testInsertNewCause() throws Exception {
        Causes newCause = new Causes();
        newCause.setCause_desc("Environmental");
        newCause.setCategory("Environment");
        newCause.setFollower_count(0);

        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent()
                .withBody(objectMapper.writeValueAsString(newCause));

        // Simulate AWS behavior
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        assertEquals(200, response.getStatusCode());
        verify(dynamoDb).putItem(putCaptor.capture());
        Map<String, AttributeValue> item = putCaptor.getValue().item();

        assertTrue(item.containsKey("cause_id"), "New cause_id should be generated");
        assertEquals("Environmental", item.get("cause_desc").s());
        assertEquals("Environment", item.get("category").s());
    }

    @Test
    void testInsertInvalidNewCause() throws Exception {
        Causes newCause = new Causes();
        newCause.setCause_desc("Environment");
        newCause.setCategory("Environment");
        newCause.setFollower_count(-1);

        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent()
                .withBody(objectMapper.writeValueAsString(newCause));
        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void testUpdateExistingCause() throws Exception {
        Causes existing = new Causes();
        existing.setCause_id("c123");
        existing.setCause_desc("Updated Desc");
        existing.setCategory("Updated Cat");
        existing.setFollower_count(42);

        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent()
                .withBody(objectMapper.writeValueAsString(existing));

        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(Map.of("cause_id", AttributeValue.fromS("c123"))).build());
        when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenReturn(UpdateItemResponse.builder().build());

        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        assertEquals(200, response.getStatusCode());
        verify(dynamoDb).updateItem(updateCaptor.capture());

        Map<String, AttributeValue> exprVals = updateCaptor.getValue().expressionAttributeValues();
        assertTrue(exprVals.containsKey(":desc"));
        assertEquals("Updated Desc", exprVals.get(":desc").s());
        assertEquals("Updated Cat", exprVals.get(":cat").s());
    }

    @Test
    void testInvalidInputMissingDesc() throws Exception {
        Causes bad = new Causes();
        bad.setCategory("SomeCat");
        bad.setFollower_count(5);

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                new APIGatewayProxyRequestEvent().withBody(objectMapper.writeValueAsString(bad)),
                context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid input"));

        verify(dynamoDb, never()).putItem((PutItemRequest) any());
        verify(dynamoDb, never()).updateItem((UpdateItemRequest) any());
    }

    @Test
    void testUpdateNonexistentCause() throws Exception {
        Causes missing = new Causes();
        missing.setCause_id("c999");
        missing.setCause_desc("Foo");
        missing.setCategory("Bar");
        missing.setFollower_count(3);

        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent()
                .withBody(objectMapper.writeValueAsString(missing));

        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());

        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("failed to update"));
    }

    @Test
    void testExceptionReturns500() {
        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent()
                .withBody("not-json");

        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Unexpected server error"));
    }

    @Test
    public void testDefaultConstructor() {
        // Test the default constructor coverage
        UpdateCausesHandler handler = new UpdateCausesHandler();
        assertNotNull(handler);

        // Verify DynamoDbClient was created (using reflection to access private field)
        try {
            Field dynamoDbField = UpdateCausesHandler.class.getDeclaredField("dynamoDb");
            dynamoDbField.setAccessible(true);
            DynamoDbClient dynamoDb = (DynamoDbClient) dynamoDbField.get(handler);
            assertNotNull(dynamoDb);
        } catch (Exception e) {
            fail("Failed to verify DynamoDbClient creation: " + e.getMessage());
        }
    }

}
