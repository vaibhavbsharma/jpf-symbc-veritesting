SET TARGET_CLASSPATH_WALA="C:/Users/whalen/Documents/git/vaibhav/jpf-symbc-veritesting/build/examples"
ECHO %TARGET_CLASSPATH_WALA%
java -cp . -Djava.library.path=C:/Users/whalen/Documents/git/vaibhav/jpf-symbc-veritesting/lib -Xmx4096m -ea -jar C:/Users/whalen/Documents/git/vaibhav/jpf-core-veritesting/build/RunJPF.jar C:/Users/whalen/Documents/git/vaibhav/jpf-symbc-veritesting/src/examples/gov.nasa.jpf.symbc.VeritestingPerf.jpf
