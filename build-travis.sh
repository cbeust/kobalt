ulimit -s 1082768

#java -Xmx2048m -jar $(dirname $0)/kobalt/wrapper/kobalt-wrapper.jar $* clean
java -Xmx2048m -jar $(dirname $0)/kobalt/wrapper/kobalt-wrapper.jar $* clean assemble test --parallel




