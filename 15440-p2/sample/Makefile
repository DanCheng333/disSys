all: Proxy.class IServer.class Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
proxy1:
	env proxyport15440=17111 java Proxy 127.0.0.1 27111 tmp 100000 &

server1:
	java Server 27111 . &

rmCache:
	rm -rf tmp/*

testCat:
	env proxyport15440=17117 env LD_PRELOAD=../lib/lib440lib.so ../tools/440cat hello.txt

testRead:
	env proxyport15440=17117 env LD_PRELOAD=../lib/lib440lib.so ../tools/440read hello.txt

testWrite:
	env proxyport15440=17117 env LD_PRELOAD=../lib/lib440lib.so ../tools/440write hello.txt
		