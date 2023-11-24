#include "jsonrpc_io.h"
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <fcntl.h>
#include <event.h>
#include <sys/timerfd.h>

#define CHECK_MALLOC(p)             \
	if(!p) {                        \
		strerror("Out of memory!"); \
		return -1;                  \
	}


void socket_cb(int fd, short event, void* arg) {
    printf("hello socket");
}

int set_non_blocking(const int fd) {
    int flags = fcntl(fd, F_GETFL);
    if (flags < 0)
        return flags;

    flags |= O_NONBLOCK;
    if (fcntl(fd, F_SETFL, flags) < 0)
        return -1;

    return 0;
}


int connect_server(jsonrpc_server_t* server) {
    struct sockaddr_in server_addr;

    memset(&server_addr, 0, sizeof(struct sockaddr_in));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(server->port);

    const struct hostent* hp = gethostbyname(server->host);
    if (hp == NULL) {
        perror("here");
        return -1;
    }
    memcpy(&(server_addr.sin_addr.s_addr), hp->h_name, hp->h_length);

    //TODO
    const int sockfd = socket(AF_INET, SOCK_STREAM, 0);

    if (connect(sockfd, (struct sockaddr *)&server_addr,
                sizeof(struct sockaddr_in))) {
        return -1;
    }

    if (set_non_blocking(sockfd) != 0) {
        return -1;
    }

    server->socket = sockfd;
    server->status = 1;
    server->conn_attempts = 0;

    struct event* socket_ev = malloc(sizeof(struct event));
    if (socket_ev == NULL) {
        return -1;
    }
    event_set(socket_ev, sockfd, EV_READ | EV_PERSIST, socket_cb, server);
    event_add(socket_ev, NULL);
    server->ev = socket_ev;
    return 0;
}
