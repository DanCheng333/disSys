all: Proxy.class IServer.class Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
proxy1:
	env proxyport15440=15317 java Proxy 127.0.0.1 23531 tmp 100000 &

server1:
	java Server 23531 . &

rmCache:
	rm -rf tmp/*

testCat:
	env proxyport15440=15317 env LD_PRELOAD=../lib/lib440lib.so ../tools/440cat hello.txt

testRead:
	env proxyport15440=15317 env LD_PRELOAD=../lib/lib440lib.so ../tools/440read hello.txt

testWrite:
	env proxyport15440=15317 cat Makefile | env LD_PRELOAD=../lib/lib440lib.so ../tools/440write hello.txt
		