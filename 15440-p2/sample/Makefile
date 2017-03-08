all: Proxy.class IServer.class Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
proxy1:
	env proxyport15440=13339 java Proxy 127.0.0.1 26114 tmp 100000 &

server1:
	env proxyport15440=13339 java Server 26114 . &

rmCache:
	rm -rf tmp/*

testCat:
	env proxyport15440=13339 env LD_PRELOAD=../lib/lib440lib.so ../tools/440cat hello.txt

testRead:
	env proxyport15440=13339 env LD_PRELOAD=../lib/lib440lib.so ../tools/440read hello.txt

testWrite:
	env proxyport15440=13339 env LD_PRELOAD=../lib/lib440lib.so ../tools/440write hello.txt
		