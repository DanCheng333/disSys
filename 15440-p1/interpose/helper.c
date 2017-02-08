#include "helper.h"

#include <string.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/socket.h>
#define MAX_BUF_LEN 15440

char *buf[MAX_BUF_LEN];
bool sendInt(int sockfd, int32_t n) {
    if (send(sockfd,&n,4,0) > 0) {
        return true;
    }
    return false;
}



