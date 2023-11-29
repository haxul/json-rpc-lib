package org.starodubov;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.starodubov.core.JsonRpcServer;
import org.starodubov.reqhandler.JsonRpcMethod;

import java.util.List;

public class Main {
    //EXAMPLE
    public static void main(String[] args) {
        var jsonRpcServer = new JsonRpcServer(
                3030,
                new ObjectMapper(),
                List.of(new JsonRpcMethod() {
                    @Override
                    public JsonNode doHandle(JsonNode json) {
                        System.out.println("doHandle " + json);
                        try {
                            return new ObjectMapper().readTree("{\"jsonrpc\": \"2.0\", \"result\": 7, \"id\": \"1\"}");
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public JsonNode doHandle() {
                        System.out.println("no params");
                        return new TextNode("done without parameters");
                    }

                    @Override
                    public String getMethodName() {
                        return "subtract";
                    }
                })
        );
        jsonRpcServer.startOnNewThread();
    }
}