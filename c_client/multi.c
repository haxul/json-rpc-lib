//
// Created by haxul on 28.11.23.
//
// Simple example of client.
// Client prints received messages to stdout and sends from stdin.

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <signal.h>
#include <unistd.h>
#include <sys/select.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <string.h>


#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>

/* Maximum bytes that can be send() or recv() via net by one call.
 * It's a good idea to test sending one byte by one.
 */
#define MAX_SEND_SIZE 100

/* Size of send queue (messages). */
#define MAX_MESSAGES_BUFFER_SIZE 10

#define SENDER_MAXSIZE 128
#define DATA_MAXSIZE 512

#define SERVER_IPV4_ADDR "127.0.0.1"
#define SERVER_LISTEN_PORT 3030

// message --------------------------------------------------------------------

typedef struct {
    char sender[SENDER_MAXSIZE];
    char data[DATA_MAXSIZE];
} message_t;

int prepare_message(char* sender, char* data, message_t* message) {
    sprintf(message->sender, "%s", sender);
    sprintf(message->data, "%s", data);
    return 0;
}

int print_message(message_t* message) {
    printf("Message: \"%s: %s\"\n", message->sender, message->data);
    return 0;
}

// message queue --------------------------------------------------------------

typedef struct {
    int size;
    message_t* data;
    int current;
} message_queue_t;

int create_message_queue(int queue_size, message_queue_t* queue) {
    queue->data = calloc(queue_size, sizeof(message_t));
    queue->size = queue_size;
    queue->current = 0;

    return 0;
}

void delete_message_queue(message_queue_t* queue) {
    free(queue->data);
    queue->data = NULL;
}

int enqueue(message_queue_t* queue, message_t* message) {
    if (queue->current == queue->size)
        return -1;

    memcpy(&queue->data[queue->current], message, sizeof(message_t));
    queue->current++;

    return 0;
}

int dequeue(message_queue_t* queue, message_t* message) {
    if (queue->current == 0)
        return -1;

    memcpy(message, &queue->data[queue->current - 1], sizeof(message_t));
    queue->current--;

    return 0;
}

int dequeue_all(message_queue_t* queue) {
    queue->current = 0;

    return 0;
}

// peer -----------------------------------------------------------------------

typedef struct {
    int socket;
    struct sockaddr_in addres;

    /* Messages that waiting for send. */
    message_queue_t send_buffer;

    /* Buffered sending message.
     *
     * In case we doesn't send whole message per one call send().
     * And current_sending_byte is a pointer to the part of data that will be send next call.
     */
    message_t sending_buffer;
    size_t current_sending_byte;

    /* The same for the receiving message. */
    message_t receiving_buffer;
    size_t current_receiving_byte;
} peer_t;

int delete_peer(peer_t* peer) {
    close(peer->socket);
    delete_message_queue(&peer->send_buffer);
}

int create_peer(peer_t* peer) {
    create_message_queue(MAX_MESSAGES_BUFFER_SIZE, &peer->send_buffer);

    peer->current_sending_byte = -1;
    peer->current_receiving_byte = 0;

    return 0;
}

char* peer_get_addres_str(peer_t* peer) {
    static char ret[INET_ADDRSTRLEN + 10];
    char peer_ipv4_str[INET_ADDRSTRLEN];
    inet_ntop(AF_INET, &peer->addres.sin_addr, peer_ipv4_str, INET_ADDRSTRLEN);
    sprintf(ret, "%s:%d", peer_ipv4_str, peer->addres.sin_port);

    return ret;
}

int peer_add_to_send(peer_t* peer, message_t* message) {
    return enqueue(&peer->send_buffer, message);
}

/* Receive message from peer and handle it with message_handler(). */
int receive_from_peer(peer_t* peer, int (*message_handler)(message_t*)) {
    printf("Ready for recv() from %s.\n", peer_get_addres_str(peer));

    size_t len_to_receive;
    ssize_t received_count;
    size_t received_total = 0;
    do {
        // Is completely received?
        if (peer->current_receiving_byte >= sizeof(peer->receiving_buffer)) {
            message_handler(&peer->receiving_buffer);
            peer->current_receiving_byte = 0;
        }

        // Count bytes to send.
        len_to_receive = sizeof(peer->receiving_buffer) - peer->current_receiving_byte;
        if (len_to_receive > MAX_SEND_SIZE)
            len_to_receive = MAX_SEND_SIZE;

        printf("Let's try to recv() %zd bytes... ", len_to_receive);
        received_count = recv(peer->socket, (char *)&peer->receiving_buffer + peer->current_receiving_byte,
                              len_to_receive, MSG_DONTWAIT);
        if (received_count < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                printf("peer is not ready right now, try again later.\n");
            }
            else {
                perror("recv() from peer error");
                return -1;
            }
        }
        else if (received_count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
            break;
        }
        // If recv() returns 0, it means that peer gracefully shutdown. Shutdown client.
        else if (received_count == 0) {
            printf("recv() 0 bytes. Peer gracefully shutdown.\n");
            return -1;
        }
        else if (received_count > 0) {
            peer->current_receiving_byte += received_count;
            received_total += received_count;
            printf("get some text: %s\n", peer->receiving_buffer.sender);
            printf("recv() %zd bytes\n", received_count);
        }
    }
    while (received_count > 0);

    printf("Total recv()'ed %zu bytes.\n", received_total);
    return 0;
}

int send_to_peer(peer_t* peer) {
    printf("Ready for send() to %s.\n", peer_get_addres_str(peer));

    size_t len_to_send;
    ssize_t send_count;
    size_t send_total = 0;
    do {
        // If sending message has completely sent and there are messages in queue, why not send them?
        if (peer->current_sending_byte < 0 || peer->current_sending_byte >= sizeof(peer->sending_buffer)) {
            printf("There is no pending to send() message, maybe we can find one in queue... ");
            if (dequeue(&peer->send_buffer, &peer->sending_buffer) != 0) {
                peer->current_sending_byte = -1;
                printf("No, there is nothing to send() anymore.\n");
                break;
            }
            printf("Yes, pop and send() one of them.\n");
            peer->current_sending_byte = 0;
        }

        // Count bytes to send.
        len_to_send = sizeof(peer->sending_buffer) - peer->current_sending_byte;
        if (len_to_send > MAX_SEND_SIZE)
            len_to_send = MAX_SEND_SIZE;

        printf("Let's try to send() %zd bytes... ", len_to_send);
        send_count = send(peer->socket, (char *)&peer->sending_buffer + peer->current_sending_byte, len_to_send, 0);
        if (send_count < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                printf("peer is not ready right now, try again later.\n");
            }
            else {
                perror("send() from peer error");
                return -1;
            }
        }
        // we have read as many as possible
        else if (send_count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
            break;
        }
        else if (send_count == 0) {
            printf("send()'ed 0 bytes. It seems that peer can't accept data right now. Try again later.\n");
            break;
        }
        else if (send_count > 0) {
            peer->current_sending_byte += send_count;
            send_total += send_count;
            printf("send()'ed %zd bytes.\n", send_count);
        }
    }
    while (send_count > 0);

    printf("Total send()'ed %zu bytes.\n", send_total);
    return 0;
}

// common ---------------------------------------------------------------------

/* Reads from stdin and create new message. This message enqueues to send queueu. */
int read_from_stdin(char* read_buffer, size_t max_len) {
    memset(read_buffer, 0, max_len);

    ssize_t read_count = 0;
    ssize_t total_read = 0;

    do {
        read_count = read(STDIN_FILENO, read_buffer, max_len);
        if (read_count < 0 && errno != EAGAIN && errno != EWOULDBLOCK) {
            perror("read()");
            return -1;
        }
        else if (read_count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
            break;
        }
        else if (read_count > 0) {
            total_read += read_count;
            if (total_read > max_len) {
                printf("Message too large and will be chopped. Please try to be shorter next time.\n");
                fflush(STDIN_FILENO);
                break;
            }
        }
    }
    while (read_count > 0);

    size_t len = strlen(read_buffer);
    if (len > 0 && read_buffer[len - 1] == '\n')
        read_buffer[len - 1] = '\0';

    printf("Read from stdin %zu bytes. Let's prepare message to send.\n", strlen(read_buffer));

    return 0;
}

peer_t server;

void shutdown_properly(int code);

void handle_signal_action(int sig_number) {
    if (sig_number == SIGINT) {
        printf("SIGINT was catched!\n");
        shutdown_properly(EXIT_SUCCESS);
    }
    else if (sig_number == SIGPIPE) {
        printf("SIGPIPE was catched!\n");
        shutdown_properly(EXIT_SUCCESS);
    }
}

int setup_signals() {
    struct sigaction sa;
    sa.sa_handler = handle_signal_action;
    if (sigaction(SIGINT, &sa, 0) != 0) {
        perror("sigaction()");
        return -1;
    }
    if (sigaction(SIGPIPE, &sa, 0) != 0) {
        perror("sigaction()");
        return -1;
    }

    return 0;
}

int get_client_name(int argc, char** argv, char* client_name) {
    if (argc > 1)
        strcpy(client_name, argv[1]);
    else
        strcpy(client_name, "no name");

    return 0;
}

int connect_server(peer_t* server) {
    // create socket
    server->socket = socket(AF_INET, SOCK_STREAM, 0);
    if (server->socket < 0) {
        perror("socket()");
        return -1;
    }

    // set up addres
    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = inet_addr(SERVER_IPV4_ADDR);
    server_addr.sin_port = htons(SERVER_LISTEN_PORT);

    server->addres = server_addr;

    if (connect(server->socket, (struct sockaddr *)&server_addr, sizeof(struct sockaddr)) != 0) {
        perror("connect()");
        return -1;
    }

    printf("Connected to %s:%d.\n", SERVER_IPV4_ADDR, SERVER_LISTEN_PORT);

    return 0;
}

int build_fd_sets(peer_t* server, fd_set* read_fds, fd_set* write_fds, fd_set* except_fds) {
    FD_ZERO(read_fds);
    FD_SET(STDIN_FILENO, read_fds);
    FD_SET(server->socket, read_fds);

    FD_ZERO(write_fds);
    // there is smth to send, set up write_fd for server socket
    if (server->send_buffer.current > 0)
        FD_SET(server->socket, write_fds);

    FD_ZERO(except_fds);
    FD_SET(STDIN_FILENO, except_fds);
    FD_SET(server->socket, except_fds);

    return 0;
}

int handle_read_from_stdin(peer_t* server, char* client_name) {
    char read_buffer[DATA_MAXSIZE]; // buffer for stdin
    if (read_from_stdin(read_buffer, DATA_MAXSIZE) != 0)
        return -1;

    // Create new message and enqueue it.
    message_t new_message;
    prepare_message(client_name, read_buffer, &new_message);
    print_message(&new_message);

    if (peer_add_to_send(server, &new_message) != 0) {
        printf("Send buffer is overflowed, we lost this message!\n");
        return 0;
    }
    printf("New message to send was enqueued right now.\n");

    return 0;
}

/* You should be careful when using this function in multythread program.
 * Ensure that server is thread-safe. */
void shutdown_properly(int code) {
    delete_peer(&server);
    printf("Shutdown client properly.\n");
    exit(code);
}

int handle_received_message(message_t* message) {
    printf("Received message from server.\n");
    print_message(message);
    return 0;
}

int main(int argc, char** argv) {
    if (setup_signals() != 0)
        exit(EXIT_FAILURE);

    char client_name[256];
    get_client_name(argc, argv, client_name);
    printf("Client '%s' start.\n", client_name);

    create_peer(&server);
    if (connect_server(&server) != 0)
        shutdown_properly(EXIT_FAILURE);

    /* Set nonblock for stdin. */
    int flag = fcntl(STDIN_FILENO, F_GETFL, 0);
    flag |= O_NONBLOCK;
    fcntl(STDIN_FILENO, F_SETFL, flag);

    fd_set read_fds;
    fd_set write_fds;
    fd_set except_fds;

    printf("Waiting for server message or stdin input. Please, type text to send:\n");

    // server socket always will be greater then STDIN_FILENO
    int maxfd = server.socket;

    while (1) {
        // Select() updates fd_set's, so we need to build fd_set's before each select()call.
        build_fd_sets(&server, &read_fds, &write_fds, &except_fds);

        int activity = select(maxfd + 1, &read_fds, &write_fds, &except_fds, NULL);

        switch (activity) {
            case -1:
                perror("select()");
                shutdown_properly(EXIT_FAILURE);

            case 0:
                // you should never get here
                printf("select() returns 0.\n");
                shutdown_properly(EXIT_FAILURE);

            default:
                /* All fd_set's should be checked. */
                if (FD_ISSET(STDIN_FILENO, &read_fds)) {
                    if (handle_read_from_stdin(&server, client_name) != 0)
                        shutdown_properly(EXIT_FAILURE);
                }

                if (FD_ISSET(STDIN_FILENO, &except_fds)) {
                    printf("except_fds for stdin.\n");
                    shutdown_properly(EXIT_FAILURE);
                }

                if (FD_ISSET(server.socket, &read_fds)) {
                    if (receive_from_peer(&server, &handle_received_message) != 0)
                        shutdown_properly(EXIT_FAILURE);
                }

                if (FD_ISSET(server.socket, &write_fds)) {
                    if (send_to_peer(&server) != 0)
                        shutdown_properly(EXIT_FAILURE);
                }

                if (FD_ISSET(server.socket, &except_fds)) {
                    printf("except_fds for server.\n");
                    shutdown_properly(EXIT_FAILURE);
                }
        }

        printf("And we are still waiting for server or stdin activity. You can type something to send:\n");
    }

    return 0;
}
