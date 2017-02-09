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

#define MAXMSGLEN 100000
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
    fprintf(stderr,"pathname lenght %d\n",oc.pathnameLen);
    char pathname[oc.pathnameLen];
    memcpy(pathname,&(buf[sizeof(oc)]),oc.pathnameLen);
    pathname[oc.pathnameLen]='\0';

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
    memcpy(&wc,buf,sizeof(wc));
    char content[size-sizeof(wc)];
    memcpy(content,&(buf[sizeof(wc)]),size-sizeof(wc));
    int ret = write(wc.fildes,content,wc.size);
    fprintf(stderr,"Write received:%d,%zu\n",
            wc.fildes,wc.size);
    sendResult(fd,ret,errno);
}

void handleRead(int fd, struct ReadCall rc, char *buf, int size) {
    fprintf(stderr,"inside handle read\n");
    memcpy(&rc,buf,sizeof(rc));
    char readBuf[rc.size];
    int ret = read(rc.fildes,readBuf,rc.size);
    struct Result res;
    res.result = ret;
    res.err = errno;
    char resWithBuf[sizeof(res)+ret];
    memcpy(resWithBuf,&res,sizeof(res));
    memcpy(&(resWithBuf[sizeof(res)]),readBuf,ret);

    fprintf(stderr,
            "Read received fildes %d,size %d, return ret %d, readBuf %s\n",
            rc.fildes,rc.size,ret,readBuf);
    send(fd,resWithBuf,sizeof(res)+ret,0);
}

//Recv and Fill the inputBuf till it reaches inputSize
void fillInputBuf(int sessfd,char *buf,char *inputBuf,
        int rvInputLen,int inputSize) {
    while (rvInputLen < inputSize) {
        int rv=recv(sessfd, buf, inputSize-rvInputLen, 0);
        fprintf(stderr,"rvInputLen1 %d\n",rvInputLen);
        memcpy(&(inputBuf[rvInputLen]),buf,rv);
        rvInputLen += rv;
        fprintf(stderr,"rvInputLen %d\n",rvInputLen);
    }
}
int main(int argc, char**argv) {
	char buf[MAXMSGLEN+1];
	char *serverport;
	unsigned short port;
	int sockfd, sessfd, rv, i;
	struct sockaddr_in srv, cli;
	socklen_t sa_size;

        struct SysCall sc;
        struct OpenCall oc;
        struct CloseCall cc;
        struct WriteCall wc;
        int rvInputLen;
		char inputBuf[MAXMSGLEN];
	// Get environment variable indicating the port of the server
	serverport = getenv("serverport4444");
	if (serverport) port = (unsigned short)atoi(serverport);
	else port=4444;//port=15440;

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
        struct ReadCall rc;
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
                    fillInputBuf(sessfd,buf,inputBuf,rvInputLen,sc.inputSize);
                    handleOpen(sessfd,oc,inputBuf,sc.inputSize);
                    continue;
                case WRITE:
                    fillInputBuf(sessfd,buf,inputBuf,rvInputLen,sc.inputSize);
                    handleWrite(sessfd,wc,inputBuf,sc.inputSize);
                    continue;
                case CLOSE:
                    fillInputBuf(sessfd,buf,inputBuf,rvInputLen,sc.inputSize);
                    handleClose(sessfd,cc,inputBuf,sc.inputSize);
                    continue;  //??
                case READ:
                    fillInputBuf(sessfd,buf,inputBuf,rvInputLen,sc.inputSize);
                    handleRead(sessfd,rc,inputBuf,sc.inputSize);
                    continue;
            }
        }
        fprintf(stderr,"Either client closed connection, or error rv%d\n",rv);
		// either client closed connection, or error
		if (rv<0) err(1,0);
		close(sessfd);
	}
	// close socket
	close(sockfd);

	return 0;
}
