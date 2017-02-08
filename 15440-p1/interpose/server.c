#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <string.h>
#include <unistd.h>
#include <err.h>
#include <errno.h>
#include "helper.h"

#define MAXMSGLEN 100
void sendResult(int fd, int ret, int err) {
    struct Result res;
    char resBuf[sizeof(res)];
    res.result = ret;
    res.err = err;
    memcpy(resBuf,&res,sizeof(res));
    send(fd,resBuf,sizeof(res),0);
}
void handleOpen(int fd, struct OpenCall oc, char *buf, int size) {
    memcpy(&oc,buf,sizeof(oc));
    char pathname[size-sizeof(oc)+1];
    memcpy(pathname,&(buf[sizeof(oc)]),size-sizeof(oc));
    pathname[size-sizeof(oc)+1]='\0';
    int ret = open(pathname,oc.flags,oc.mode);
    fprintf(stderr,"Open received: %s,%d,%d,result %d\n"
            ,pathname,oc.flags,oc.mode,ret);
    sendResult(fd,ret,errno);
}

void handleClose(int fd, struct CloseCall cc, char *buf, int size) {
    memcpy(&cc,buf,sizeof(cc));
    int ret = close(cc.fildes);
    fprintf(stderr,"Close received:%d,result %d\n",
            cc.fildes,ret);
    sendResult(fd,ret,errno);
}

void handleWrite(int fd, struct WriteCall wc, char *buf,int size) {
    char content[size];
    memcpy(&wc,buf,sizeof(wc));
    memcpy(content,&(buf[sizeof(wc)]),size-sizeof(wc));
    int ret = write(wc.fildes,content,wc.size);
    fprintf(stderr,"Write received:%d,%zu\n",
            wc.fildes,wc.size);
    sendResult(fd,ret,errno);
}

int main(int argc, char**argv) {
	char buf[MAXMSGLEN+1];
	char *serverport;
	unsigned short port;
	int sockfd, sessfd, rv, i;
	struct sockaddr_in srv, cli;
	socklen_t sa_size;

	// Get environment variable indicating the port of the server
	serverport = getenv("serverport4212");
	if (serverport) port = (unsigned short)atoi(serverport);
	else port=4212;//port=15440;

	// Create socket
	sockfd = socket(AF_INET, SOCK_STREAM, 0);	// TCP/IP socket
	if (sockfd<0) err(1, 0);			// in case of error

	// setup address structure to indicate server port
	memset(&srv, 0, sizeof(srv));			// clear it first
	srv.sin_family = AF_INET;			// IP family
	srv.sin_addr.s_addr = htonl(INADDR_ANY);	// don't care IP address
	srv.sin_port = htons(port);			// server port

	// bind to our port
	rv = bind(sockfd, (struct sockaddr*)&srv, sizeof(struct sockaddr));
	if (rv<0) err(1,0);

	// start listening for connections
	rv = listen(sockfd, 5);
	if (rv<0) err(1,0);

	// main server loop, handle clients one at a time, quit after 10 clients
	for( i=0; i<10; i++ ) {

		// wait for next client, get session socket
		sa_size = sizeof(struct sockaddr_in);
		sessfd = accept(sockfd, (struct sockaddr *)&cli, &sa_size);
		if (sessfd<0) err(1,0);
        struct SysCall sc;
        struct OpenCall oc;
        struct CloseCall cc;
        struct WriteCall wc;
        int rvInputLen;
		char inputBuf[MAXMSGLEN];
        // get messages and send replies to this client, until it goes away
        while ( (rv=recv(sessfd, buf, 8, 0)) > 0) {
            fprintf(stderr,"\nServer recv %d\n",rv);
            rvInputLen = 0;
            memcpy(&sc,buf,sizeof(sc));
            fprintf(stderr,"Inputsize %d\n",sc.inputSize);
            switch (sc.sysCallName) {
                case OPEN:
                    while (rvInputLen < sc.inputSize) {
                        rv=recv(sessfd, buf, sc.inputSize-rvInputLen, 0);
                        fprintf(stderr,"Open rvInputLen1 %d\n",rvInputLen);
                        memcpy(&(inputBuf[rvInputLen]),buf,rv);
                        rvInputLen += rv;
                        fprintf(stderr,"Open rvInputLen %d\n",rvInputLen);
                    }
                    handleOpen(sessfd,oc,inputBuf,sc.inputSize);
                    continue;
                case WRITE:
                    while (rvInputLen < sc.inputSize) {
                        rv=recv(sessfd, buf, sc.inputSize-rvInputLen, 0);
                        fprintf(stderr,"Write rvInputLen1 %d\n",rvInputLen);
                        memcpy(&(inputBuf[rvInputLen]),buf,rv);
                        rvInputLen += rv;
                        fprintf(stderr,"Write rvInputLen %d\n",rvInputLen);
                    }
                    handleWrite(sessfd,wc,inputBuf,sc.inputSize);
                    continue;
                case CLOSE:
                    while (rvInputLen < sc.inputSize) {
                        rv=recv(sessfd, buf, sc.inputSize-rvInputLen, 0);
                        fprintf(stderr,"Close rvInputLen1 %d\n",rvInputLen);
                        memcpy(&(inputBuf[rvInputLen]),buf,rv);
                        rvInputLen += rv;
                        fprintf(stderr,"Close rvInputLen %d\n",rvInputLen);
                    }
                    handleClose(sessfd,cc,inputBuf,sc.inputSize);
                    continue;  //??
            }
        }
        fprintf(stderr,"Either client closed connection, or error\n");
		// either client closed connection, or error
		if (rv<0) err(1,0);
		close(sessfd);
	}
	// close socket
	close(sockfd);

	return 0;
}

