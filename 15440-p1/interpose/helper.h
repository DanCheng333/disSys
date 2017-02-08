#ifndef HELPER_H_
#define HELPER_H_
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
enum SysCallName{OPEN,WRITE,CLOSE};
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
#endif
