all: Proxy.class IServer.class Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
env:
	env proxyport15440=11140 
	
proxy1:
	java Proxy 127.0.0.1 29110 /tmp/cache 100000 &

server1:
	java Server 29110 fileroot &

testCat:
	rm -rf /tmp/cache/*
	env LD_PRELOAD=../lib/lib440lib.so ../tools/440read hello.txt
