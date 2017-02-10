#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <string.h>
#include <unistd.h>
#include <err.h>
#include <errno.h>
#include "helper.h"
#define MAXMSGLEN 100

int sockfd;
char buf[MAXMSGLEN+1];
int clientSocket() {
  char *serverip;
  char *serverport;
  unsigned short port;
  int rv;
  struct sockaddr_in srv;

  // Get environment variable indicating the ip address of the server
  serverip = getenv("server12444");
  if (!serverip) {
    serverip = "127.0.0.1";
  }
  // Get environment variable indicating the port of the server
  serverport = getenv("serverport12444");
  if (!serverport) {
    serverport = "12444";
  }
  port = (unsigned short)atoi(serverport);
  // Create socket
  sockfd = socket(AF_INET, SOCK_STREAM, 0);// TCP/IP socket
  if (sockfd<0) {
    return -1;
  }// in case of error
  // setup address structure to point to server
  memset(&srv, 0, sizeof(srv));// clear it first
  srv.sin_family = AF_INET;// IP family
  srv.sin_addr.s_addr = inet_addr(serverip);// IP address of server
  srv.sin_port = htons(port);// server port
  // actually connect to the server
  rv = connect(sockfd, (struct sockaddr*)&srv, sizeof(struct sockaddr));
  if (rv<0) {
    return -1;
  }
  return sockfd;
}

#include <dlfcn.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdarg.h>
#include "../include/dirtree.h"
char *msg;
// The following line declares a function pointer with the same prototype as the open function.
int (*orig_open)(const char *pathname, int flags, ...);  // mode_t mode is needed when flags includes O_CREAT
int (*orig_close)(int fildes);
ssize_t (*orig_read)(int fildes, void *buf, size_t size);
ssize_t (*orig_write)(int fildes, const void *buf, size_t size);
off_t (*orig_lseek)(int fildes, off_t offset, int whence);
int (*orig___xstat)(int ver, const char *path, struct stat *buf);
int (*orig_unlink)(const char *path);
ssize_t (*orig_getdirentries)(int fd, char *buf, size_t nbytes , off_t *basep);
struct dirtreenode* (*orig_getdirtree)( const char *path );
void (*orig_freedirtree) ( struct dirtreenode* dt );

struct SysCall sc;
struct Result res;

void getResult(int sockfd) {
  char resBuf[sizeof(res)];
  recv(sockfd,resBuf,sizeof(res),0);
  memcpy(&res,resBuf,sizeof(res));
}

// This is our replacement for the open function from libc.
int open(const char *pathname, int flags, ...) {
  mode_t m=0;
  if (flags & O_CREAT) {
    va_list a;
    va_start(a, flags);
    m = va_arg(a, mode_t);
    va_end(a);
  }
  fprintf(stderr,"\n\n******* OPEN *********");
  //Construct opencall struct
  struct OpenCall oc;
  oc.pathnameLen = strlen(pathname);
  oc.flags = flags;
  oc.mode = m;
  fprintf(stderr,"Sent: pathnameLen %d flags %d mode %d \n",
  oc.pathnameLen,oc.flags,oc.mode);
  char ocBuf[sizeof(oc)];
  memcpy(ocBuf,&oc,sizeof(oc));

  //Construct syscall struct
  sc.sysCallName = OPEN;
  sc.inputSize = (int)sizeof(oc)+strlen(pathname);
  char scBuf[sizeof(sc)+sizeof(oc)+strlen(pathname)];
  memcpy(scBuf,&sc,sizeof(sc));
  //Append opencall struct
  memcpy(&(scBuf[sizeof(sc)]),&oc,sizeof(oc));
  //Append pathname
  memcpy(&(scBuf[sizeof(sc)+sizeof(oc)]),pathname,strlen(pathname));
  send(sockfd,scBuf,sizeof(scBuf),0);

  //receive return value and errno
  getResult(sockfd);
  fprintf(stderr,"Received open result %d\n",res.result);
  errno = res.err;
  if (res.result == -1) { //Fail to open
    perror("Open Error");
  }
  fprintf(stderr,"\n\n******* END OF OPEN *********");
  //Success: return file descriptor
  return res.result;
}

int close(int fildes) {
  fprintf(stderr,"\n\n******* CLOSE *********");
  struct CloseCall cc;
  cc.fildes = fildes;
  char ccBuf[sizeof(cc)];
  memcpy(ccBuf,&cc,sizeof(cc));

  sc.sysCallName = CLOSE;
  sc.inputSize=(int)sizeof(cc);
  char scBuf[sizeof(sc)+sizeof(cc)];
  memcpy(scBuf,&sc,sizeof(sc));
  memcpy(&(scBuf[sizeof(sc)]),&cc,sizeof(cc));
  send(sockfd,scBuf,sizeof(scBuf),0);

  getResult(sockfd);
  errno = res.err;
  fprintf(stderr,"Received close result:%d\n",res.result);
  if (res.result == -1) {//fail to close
    perror("Close error");
  }
  fprintf(stderr,"\n\n******* END OF CLOSE *********");
  //Success return 0
  return res.result;
}

ssize_t read(int fildes, void *buf, size_t size) {
  fprintf(stderr,"\n\n******* READ *********");
  struct ReadCall rc;
  rc.fildes = fildes;
  rc.size = size;
  char rcBuf[sizeof(rc)];
  memcpy(rcBuf,&rc,sizeof(rc));
  fprintf(stderr,"Read sent fildes %d, size %zu\n",fildes,size);

  sc.sysCallName = READ;
  sc.inputSize=sizeof(rc);
  char scBuf[sizeof(sc)+sizeof(rc)];
  memcpy(scBuf,&sc,sizeof(sc));
  memcpy(&(scBuf[sizeof(sc)]),rcBuf,sizeof(rc));
  send(sockfd,scBuf,sizeof(scBuf),0);

  //Get result and copy readBuf to buf
  getResult(sockfd);
  fprintf(stderr,"After get result\n");
  int bufSize = 0;
  char readBuf[res.result];
  while (bufSize < res.result) {
    int rvSize = MIN(MAXMSGLEN,res.result-bufSize);
    int rv=recv(sockfd, buf, rvSize, 0);
    fprintf(stderr,"Read bufSize1 %d\n",bufSize);
    memcpy(&(readBuf[bufSize]),buf,rv);
    bufSize += rv;
    fprintf(stderr,"Read bufSize %d\n",bufSize);
  }
  if (res.result > 0) {
    memcpy(buf,readBuf,res.result);
  }
  if (res.result == 1) {
    perror("Read Error");
  }
  fprintf(stderr,"\n\n******* END OF READ *********");
  return res.result;

}

//buf up to size -> fildes
ssize_t write(int fildes, const void *buf, size_t size) {
  char content[size];
  memcpy(content,buf,size);

  struct WriteCall wc;
  wc.fildes = fildes;
  wc.size = size;
  char wcBuf[sizeof(wc)];
  memcpy(wcBuf,&wc,sizeof(wc));

  struct SysCall sc;
  sc.sysCallName = WRITE;;
  sc.inputSize=sizeof(wc)+size;
  char scBuf[sizeof(sc)+sizeof(wc)+size];
  memcpy(scBuf,&sc,sizeof(sc));
  memcpy(&(scBuf[sizeof(sc)]),wcBuf,sizeof(wc));
  memcpy(&(scBuf[sizeof(sc)+sizeof(wc)]),content,size);
  send(sockfd,scBuf,sizeof(scBuf),0);

  /* recv result */
  struct Result res;
  char resBuf[sizeof(res)];
  recv(sockfd,resBuf,sizeof(res),0);
  memcpy(&res,resBuf,sizeof(res));
  errno = res.err;
  fprintf(stderr,"Received write result: %d\n",res.result);
  if (res.result == -1) {
    perror("Write error");
  }
  //Success # of bytes written
  return res.result;
}
off_t lseek(int fildes, off_t offset, int whence) {
  fprintf(stderr,"\n\n******* LSEEK *********");
  fprintf(stderr,"Lseek fildes %d, offset %llu, whence %d\n",
  fildes,offset,whence);
  struct LseekCall lc;
  lc.fildes = fildes;
  lc.offset = offset;
  lc.whence = whence;
  char lcBuf[sizeof(lc)];
  memcpy(lcBuf,&lc,sizeof(lc));

  sc.sysCallName = LSEEK;
  sc.inputSize=(int)sizeof(lc);
  char scBuf[sizeof(sc)+sizeof(lc)];
  memcpy(scBuf,&sc,sizeof(sc));
  memcpy(&(scBuf[sizeof(sc)]),&lc,sizeof(lc));
  send(sockfd,scBuf,sizeof(scBuf),0);

  getResult(sockfd);
  errno = res.err;
  fprintf(stderr,"Received LSEEK result:%d\n",res.result);
  if (res.result == -1) {//fail to lseek
    perror("Lseek error");
  }
  fprintf(stderr,"\n\n******* END OF LSEEK *********");
  //Success return 0
  return res.result;
}

int __xstat(int ver, const char *path, struct stat *buf){
  fprintf(stderr,"\n\n******* XSTAT*********");
  sc.sysCallName = __XSTAT;
  sc.inputSize= 1;
  char scBuf[sizeof(sc)];
  memcpy(scBuf,&sc,sizeof(sc));
  send(sockfd,scBuf,sizeof(scBuf),0);
  return 1;
}

int unlink(const char *path){
  fprintf(stderr,"\n\n******* UNLINK *********");
  fprintf(stderr,"Unlink path %s\n",path);

  sc.sysCallName = UNLINK;
  sc.inputSize=strlen(path);
  char scBuf[sizeof(sc)+strlen(path)];
  memcpy(scBuf,&sc,sizeof(sc));
  memcpy(&(scBuf[sizeof(sc)]),path,strlen(path));
  send(sockfd,scBuf,sizeof(scBuf),0);

  getResult(sockfd);
  errno = res.err;
  fprintf(stderr,"Received UNLINK result:%d\n",res.result);
  if (res.result == -1) {//fail to lseek
    perror("Unlink error");
  }
  fprintf(stderr,"\n\n******* END OF UNLINK *********");
  //Success return 0
  return res.result;
}


ssize_t getdirentries(int fd, char *buf, size_t nbytes , off_t *basep) {
  fprintf(stderr,"\n\n******* GETDIRENTRIES*********");
  ssize_t result;
  fprintf(stderr,"fd %d, nbytes %zu basep %llu\n,sizeof ss%zu\n",fd,nbytes,*basep,sizeof(ssize_t));
  struct GetdirentriesCall gdsc;
  gdsc.fd = fd;
  gdsc.nbytes = nbytes;
  //gdsc.basep = *basep;
  char *gdscBuf[sizeof(gdsc)+sizeof(basep)];
  memcpy(gdscBuf,&gdsc,sizeof(gdsc));
  memcpy(&(gdscBuf[sizeof(gdsc)]),basep,sizeof(basep));

  sc.sysCallName = GETDIRENTRIES;
  sc.inputSize= sizeof(gdsc)+sizeof(basep);
  char scBuf[sizeof(sc)+sizeof(gdsc)+sizeof(basep)];
  memcpy(scBuf,&sc,sizeof(sc));
  memcpy(&(scBuf[sizeof(sc)]),gdscBuf,sizeof(gdsc));
  send(sockfd,scBuf,sizeof(scBuf),0);

  char resultbuf[sizeof(ssize_t)+sizeof(errno)];
  recv(sockfd,resultbuf,sizeof(resultbuf),0);
  memcpy(&result,resultbuf,sizeof(ssize_t));
  memcpy(&errno,&(resultbuf[sizeof(ssize_t)]),sizeof(errno));

  int bufSize = 0;
  int recvBuf[MAXMSGLEN];
  while (bufSize < result) {
    int rvSize = MIN(MAXMSGLEN,result-bufSize);
    int rv=recv(sockfd, recvBuf, rvSize, 0);
    fprintf(stderr,"gds bufSize1 %d\n",bufSize);
    memcpy(&(buf[bufSize]),recvBuf,rv);
    bufSize += rv;
    fprintf(stderr,"gds bufSize %d\n",bufSize);
  }

  fprintf(stderr,"received result %zu\n",result);
  fprintf(stderr,"\n\n******* END OF GETDIRENTRIES *********");
  return result;
}

struct dirtreenode* getdirtree( const char *path ) {
  fprintf(stderr,"\n\n******* GETDIRTREE *********");
  fprintf(stderr,"Unlink path %s\n",path);

  sc.sysCallName = GETDIRTREE;
  sc.inputSize=strlen(path);
  char scBuf[sizeof(sc)+strlen(path)];
  memcpy(scBuf,&sc,sizeof(sc));
  memcpy(&(scBuf[sizeof(sc)]),path,strlen(path));
  send(sockfd,scBuf,sizeof(scBuf),0);

  struct dirtreenode* result;

  perror("Getdirtree rror");
  fprintf(stderr,"\n\n******* END OF GETDIRTREE *********");
  //Success return 0
  return result;
}

void freedirtree( struct dirtreenode* dt ){
  msg = "freedirtree\n";
  send(sockfd, msg, strlen(msg), 0);
  return orig_freedirtree(dt);
}
// This function is automatically called when program is started
void _init(void) {
  sockfd = clientSocket();
  // set function pointer orig_open to point to the original open function
  orig_open = dlsym(RTLD_NEXT, "open");
  orig_close = dlsym(RTLD_NEXT,"close");
  orig_read = dlsym(RTLD_NEXT, "read");
  orig_write = dlsym(RTLD_NEXT, "write");
  orig_lseek = dlsym(RTLD_NEXT, "lseek");
  orig___xstat = dlsym(RTLD_NEXT, "__xstat");
  orig_unlink = dlsym(RTLD_NEXT, "unlink");
  orig_getdirentries = dlsym(RTLD_NEXT, "getdirentries");
  orig_getdirtree = dlsym(RTLD_NEXT, "getdirtree");
  orig_freedirtree = dlsym(RTLD_NEXT, "freedirtree");
  if (sockfd == -1) {
    fprintf(stderr,"exit sockfd = -1\n");
    exit(1);
  }
}
