all: Proxy.class IServer.class Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class
	
env:
	env proxyport15440=15921 
	
proxy1:
	java Proxy 127.0.0.1 29122 /tmp/cache 100000 &

server1:
	java Server 29122 fileroot &

testCat:
	rm -rf /tmp/cache/*
	env LD_PRELOAD=../lib/lib440lib.so ../tools/440cat hello.txt