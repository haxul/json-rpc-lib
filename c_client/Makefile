JSONRPC_FILE=src/jsonrpc_io.c
run-gcc:
	gcc  -Wall -g -std=c99 -ljson-c src/jsonrpc_io.c main.c -o main

run-clang:
	clang -Wall -g -std=c99 -ljson-c -levent $(JSONRPC_FILE) main.c -o main
