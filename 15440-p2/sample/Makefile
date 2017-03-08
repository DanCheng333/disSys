all: Proxy.class IServer.class Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
proxy1:
	env proxyport15440=13342 java Proxy 127.0.0.1 26117 tmp 100000 &

server1:
	env proxyport15440=13342 java Server 26117 . &

rmCache:
	rm -rf tmp/*

testCat:
	env proxyport15440=13342 env LD_PRELOAD=../lib/lib440lib.so ../tools/440cat hello.txt
	env proxyport15440=13342 env LD_PRELOAD=../lib/lib440lib.so ../tools/440cat world.txt

testRead:
	env proxyport15440=13342 env LD_PRELOAD=../lib/lib440lib.so ../tools/440read hello.txt
	env proxyport15440=13342 env LD_PRELOAD=../lib/lib440lib.so ../tools/440read world.txt

testWrite:
	cat world.txt | env proxyport15440=13342 env LD_PRELOAD=../lib/lib440lib.so ../tools/440write hello.txt 2
		