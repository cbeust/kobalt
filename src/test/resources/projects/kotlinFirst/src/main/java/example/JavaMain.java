package example;

public class JavaMain {
	public static void main(String[] argv) {
		System.out.println("JavaMain calling into Kotlin");
		new KotlinClass().run();
	}
}