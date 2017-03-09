all: Proxy.class IServer.class Server.class LRU.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
proxy1:
	env proxyport15440=13360 java Proxy 127.0.0.1 26135 tmp 100 &

server1:
	env proxyport15440=13360 java Server 26135 . &

rmCache:
	rm -rf tmp/*

testCat:
	env proxyport15440=13360 env LD_PRELOAD=../lib/lib440lib.so ../tools/440cat fileroot/../hello.txt

testRead:
	env proxyport15440=13360 env LD_PRELOAD=../lib/lib440lib.so ../tools/440read fileroot/.././hello.txt

testWrite:
	cat world.txt | env proxyport15440=13360 env LD_PRELOAD=../lib/lib440lib.so ../tools/440write hello.txt 2
		