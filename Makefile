all:
	javac -cp /usr/share/asm-2/lib/asm.jar *.java

test:
	java -cp .:Atlantis/build:/usr/share/asm-2/lib/asm.jar \
		-Djava.security.manager -Djava.security.policy=test.policy \
		Test
clean:
	rm *.class
