// this script expected parameter num : Int

fun fib(n: Int): Int {
    val v = if(n < 2) 1 else fib(n-1) + fib(n-2)
    //java.lang.System.out.println("fib($n)=$v")
    return v
}

fun fac(n: Int) : Int {
	return if (n == 1) 1 else n * fac(n-1)
}

//java.lang.System.out.println("num: $num")
val result = fib(num)
val factorial = fac(num)