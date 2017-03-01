all: Proxy.class IServer.class Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class
	
proxy1:
	env proxyport15440=15440 java Proxy 127.0.0.1 11122 /tmp/cache 100000 &

testCat:
	env LD_PRELOAD=../lib/lib440lib.so ../tools/440cat lh.txt