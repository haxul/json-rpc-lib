#include <stdio.h>
#include <json-c/json.h>
#include "src/jsonrpc_io.h"
#include <arpa/inet.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>
#include <stdlib.h>
#include <ctype.h>

#define PORT 8080
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


int netstring_read_fd(int fd, char** netstring) {
    int i, bytes;
    size_t len = 0;

    *netstring = NULL;

    char buffer[10] = {0};

    /* Peek at first 10 bytes, to get length and colon */
    bytes = recv(fd, buffer, 10, MSG_PEEK);

    if (bytes < 3)
        return 1;

    /* No leading zeros allowed! */
    if (buffer[0] == '0' && isdigit(buffer[1]))
        return 2;

    /* The netstring must start with a number */
    if (!isdigit(buffer[0]))
        return 3;

    /* Read the number of bytes */
    for (i = 0; i < bytes && isdigit(buffer[i]); i++) {
        /* Error if more than 9 digits */
        if (i >= 9)
            return 4;
        /* Accumulate each digit, assuming ASCII. */
        len = len * 10 + (buffer[i] - '0');
    }

    /* Read the colon */
    if (buffer[i++] != ':')
        return 5;

    /* Read the whole string from the buffer */
    size_t read_len = i + len + 1;
    char* buffer2 = malloc(read_len);
    if (!buffer2) {
        perror("Out of memory!");
        return -1;
    }
    bytes = recv(fd, buffer2, read_len, 0);

    /* Make sure we got the whole netstring */
    if (read_len > bytes)
        return 5;

    /* Test for the trailing comma */
    if (buffer2[read_len - 1] != ',')
        return 6;

    buffer2[read_len - 1] = '\0';

    int x;

    for (x = 0; x <= read_len - i - 1; x++) {
        buffer2[x] = buffer2[x + i];
    }

    *netstring = buffer2;
    return 0;
}

int main() {
    int status, valread, sock_fd;
    struct sockaddr_in serv_addr;
    char* hello = "{\"id\":\"123\"}";
    char buffer[1024] = {0};
    if ((sock_fd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        printf("\n Socket creation error \n");
        return -1;
    }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(PORT);

    if (inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr) <= 0) {
        printf("\nInvalid address/ Address not supported \n");
        return -1;
    }

    if ((status = connect(sock_fd, (struct sockaddr *)&serv_addr, sizeof(serv_addr))) < 0) {
        printf("\nConnection Failed. status: %d \n", status);
        return -1;
    }

    send(sock_fd, hello, strlen(hello), 0);
    printf("Hello message sent\n");

    char* netstring;
    const int res = netstring_read_fd(sock_fd, &netstring);
    if (res != 0) {
        fprintf(stderr, "netsring read err: %d\n", res);
        return -1;
    }
    printf("res: %s", netstring);
    json_object* json_obj = json_tokener_parse(netstring);
    if (!json_obj) {
        fprintf(stderr, "parse json err");
        return -1;
    }

    const char* json_str = json_object_to_json_string(json_obj);

    printf("\njson as a string: %s", json_str);

    close(sock_fd);
    return 0;
}
