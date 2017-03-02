all: Proxy.class IServer.class Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class
	
proxy1:
	env proxyport15440=15120 java Proxy 127.0.0.1 21222 /tmp/cache 100000 &

server1:
	java Server 21222 fileroot &

testCat:
	env proxyport15440=15120
	env LD_PRELOAD=../lib/lib440lib.so ../tools/440cat lh.txt