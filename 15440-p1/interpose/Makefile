PROGS=server
CFLAGS+=-Wall

all: mylib.so server

server: server.c helper.o
	gcc -Wall -o server server.c helper.o -L../lib

mylib.o: mylib.c
	gcc -Wall -fPIC -DPIC -c mylib.c -I../include -L../lib

mylib.so: mylib.o helper.o
	ld -shared -o mylib.so mylib.o helper.o -ldl

helper.o: helper.c helper.h
	gcc -Wall -fPIC -DPIC -c -o helper.o helper.c

clean:
	rm -f *.o *.so server

