#include "stdio.h"
#include "stdlib.h"

int main() {
    char* fileName = "testfile.txt";
    char writeBuffer[] = "hello,lol";
    char readBuffer[50];
    int fd, bytesRead;

    fd = open(fileName);
    if (fd < 0) {
        printf("%s","Cannot open file!");
        close(fd);
        return 0;
    }

    if (write(fd, writeBuffer,sizeof(writeBuffer)) < 0) {
        close(fd);
        printf("%s","size!");
        return 1;
    }
    // int fd1 = creat(fileName);
    close(fd);
    // close(fd1);
    return 1;
}