#ifndef HELPER_H_
#define HELPER_H_
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#define MIN(a,b) (((a)<(b))?(a):(b))
enum SysCallName{
  OPEN,
  WRITE,
  CLOSE,
  READ,
  LSEEK,
  __XSTAT,
  UNLINK,
  GETDIRENTRIES,
  GETDIRTREE
};
struct __attribute__((__packed__)) SysCall {
  enum SysCallName sysCallName;
  int inputSize;
};
struct __attribute__((__packed__)) Result {
  int result;
  int err;
};
struct __attribute__((__packed__)) OpenCall {
  int pathnameLen;
  int flags;
  int mode;
};

struct __attribute__((__packed__)) CloseCall {
  int fildes;
};

struct __attribute__((__packed__)) WriteCall {
  int fildes;
  size_t size;
};

struct __attribute__((__packed__)) ReadCall {
  int fildes;
  size_t size;
};

struct __attribute__((__packed__)) LseekCall {
  int fildes;
  off_t offset;
  int whence;
};

struct __attribute__((__packed__)) XstatCall {

};

struct __attribute__((__packed__)) GetdirentriesCall {
  int fd;
  size_t nbytes;
  //off_t basep;

};

struct __attribute__((__packed__)) GetdirtreeCall {

};
#endif
