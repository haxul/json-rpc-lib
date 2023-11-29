package org.starodubov;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.starodubov.core.JsonRpcServer;
import org.starodubov.reqhandler.JsonRpcEntity;
import org.starodubov.reqhandler.JsonRpcMethod;
import org.starodubov.util.JsonRpcCode;

import java.util.List;
/*
{"jsonrpc": "2.0", "method": "subtract", "params": {"one": 1, "two": "helloparam"}, "id": 1}

 */
public class Main {
    //EXAMPLE
    public static void main(String[] args) {
        final var jsonRpcServer = new JsonRpcServer(
                3030,
                new ObjectMapper(),
                List.of(new SubtractJsonRpcMethod())
        );

        jsonRpcServer.startOnNewThread();
    }

    record User(int age, String name) {
    }

    public static class SubtractJsonRpcMethod implements JsonRpcMethod<User> {

        @Override
        public JsonRpcEntity<User> doMethod(JsonNode json) {
            return new JsonRpcEntity<>(JsonRpcCode.INVALID_REQ, "some err");
        }

        @Override
        public String methodName() {
            return "subtract";
        }
    }
}
