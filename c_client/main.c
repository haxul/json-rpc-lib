#include <stdio.h>
#include <json-c/json.h>
#include "src/jsonrpc_io.h"
/*
void json_parse() {
    char* string = "{ \"colors\" : \"hello world\", \"continents\" : 7, \"oceans\" : 5 }";
    json_object* jobj = json_tokener_parse(string);
    json_type type;
    json_object_object_foreach(jobj, key, val) {
        type = json_object_get_type(val);
        switch (type) {
            case json_type_string:
                printf("value: %s\n", json_object_get_string(val));
                break;
        }
    }
}
*/
int main() {
    jsonrpc_server_t server = {
        .port = 3000,
        .socket = 1,
        .status = 100,
        .conn_attempts = 0,
        .host = "localhost"
    };
    int res = connect_server(&server);
    printf("%d", res);
}
