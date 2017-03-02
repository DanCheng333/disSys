all: Proxy.class IServer.class Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
proxy1:
	env proxyport15440=11245 java Proxy 127.0.0.1 20325 /tmp/cache 100000 &

server1:
	java Server 20325 fileroot &

testCat:
	rm -rf /tmp/cache/*
	env proxyport15440=11245 env LD_PRELOAD=../lib/lib440lib.so ../tools/440read hello.txt
