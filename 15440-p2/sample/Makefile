all: Proxy.class IServer.class Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class
	
proxy1:
	env proxyport15440=15121 java Proxy 127.0.0.1 21122 /tmp/cache 100000 &

server1:
	java Server 21122 fileroot &

testCat:
	rm -rf /tmp/cache/*
	env LD_PRELOAD=../lib/lib440lib.so ../tools/440cat hello.txt