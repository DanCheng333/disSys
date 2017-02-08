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
    printf("\n size of pathLen %d",oc.pathnameLen);
    printf("\n size of flags %d",oc.flags);
    char pathname[size-sizeof(oc)+1];
    printf("\n size of pathLen %d",size-sizeof(oc));
    memcpy(pathname,&(buf[sizeof(oc)]),size-sizeof(oc));
    printf("\n size of pathname %s",pathname);
    pathname[size-sizeof(oc)+1]='\0';
    int ret = open(pathname,oc.flags,oc.mode);
    sendResult(fd,ret,errno);
}

void handleClose(int fd, struct CloseCall cc, char *buf, int size) {
    printf("\n close");
    memcpy(&cc,buf,sizeof(cc));
    printf("\nserver close fildes %d",cc.fildes);
    int ret = close(cc.fildes);
    sendResult(fd,ret,errno);
}

void handleWrite(int fd, struct WriteCall wc, char *buf,int size) {
    char content[size];
    printf("in handle write ");
    memcpy(&wc,buf,sizeof(wc));
    printf("\n wc.fd %d size %d",wc.fildes,wc.size);
    memcpy(content,&(buf[sizeof(wc)]),size-sizeof(wc));
    int ret = write(wc.fildes,content,size-sizeof(wc));
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
            printf("\n server recv %d",rv);
            rvInputLen = 0;
            //if (rv = 8) {
            memcpy(&sc,buf,sizeof(sc));
            printf("\n inputsize %d",sc.inputSize);
            switch (sc.sysCallName) {
                case OPEN:
                    while (rvInputLen < sc.inputSize) {
                        rv=recv(sessfd, buf, sc.inputSize-rvInputLen, 0);
                        printf("\n open rvInputLen1 %d",rvInputLen);
                        memcpy(&(inputBuf[rvInputLen]),buf,rv);
                        rvInputLen += rv;
                        printf("\n open rvInputLen %d",rvInputLen);
                    }
                    handleOpen(sessfd,oc,inputBuf,sc.inputSize);
                    printf("\nend of handle open");
                    continue;
                case WRITE:
                    while (rvInputLen < sc.inputSize) {
                        rv=recv(sessfd, buf, sc.inputSize-rvInputLen, 0);
                        printf("\n write rvInputLen1 %d",rvInputLen);
                        memcpy(&(inputBuf[rvInputLen]),buf,rv);
                        rvInputLen += rv;
                        printf("\n open rvInputLen %d",rvInputLen);
                    }
                    handleWrite(sessfd,wc,inputBuf,sc.inputSize);
                    continue;
                case CLOSE:
                    while (rvInputLen < sc.inputSize) {
                        rv=recv(sessfd, buf, sc.inputSize-rvInputLen, 0);
                        printf("\n close rvInputLen1 %d",rvInputLen);
                        memcpy(&(inputBuf[rvInputLen]),buf,rv);
                        rvInputLen += rv;
                        printf("\n close rvInputLen %d",rvInputLen);
                    }
                    handleClose(sessfd,cc,inputBuf,sc.inputSize);
                    continue;  //??
            }
        }
        printf("\n out of recv");
		// either client closed connection, or error
		if (rv<0) err(1,0);
		close(sessfd);
	}
	// close socket
	close(sockfd);

	return 0;
}

