all: Proxy.class IServer.class Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
proxy1:
	env proxyport15440=17177 java Proxy 127.0.0.1 27177 tmp 100000 &

server1:
	java Server 27177 . &

testCat:
	rm -rf tmp/*
	env proxyport15440=17177 env LD_PRELOAD=../lib/lib440lib.so ../tools/440read hello.txt
