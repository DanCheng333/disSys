all: Proxy.class IServer.class Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
proxy1:
	env proxyport15440=13346 java Proxy 127.0.0.1 26121 tmp 100000 &

server1:
	env proxyport15440=13346 java Server 26121 . &

rmCache:
	rm -rf tmp/*

testCat:
	env proxyport15440=13346 env LD_PRELOAD=../lib/lib440lib.so ../tools/440cat hello.txt

testRead:
	env proxyport15440=13346 env LD_PRELOAD=../lib/lib440lib.so ../tools/440read hello.txt

testWrite:
	cat world.txt | env proxyport15440=13346 env LD_PRELOAD=../lib/lib440lib.so ../tools/440write hello.txt 2
		