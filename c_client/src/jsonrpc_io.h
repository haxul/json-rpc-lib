#ifndef JSONRPC_ID_H
#define JSONRPC_ID_H

#include <event.h>

struct jsonrpc_server {
    struct event* ev;
    char* host;
    int port, socket, status, conn_attempts;
};

typedef struct jsonrpc_server jsonrpc_server_t;

int connect_server(jsonrpc_server_t* server);

#endif //JSONRPC_ID_H
