java -Xmx2048m -jar $(dirname $0)/kobalt/wrapper/kobalt-wrapper.jar $* clean
java -Xmx2048m -jar $(dirname $0)/kobalt/wrapper/kobalt-wrapper.jar $* assemble test --parallel




