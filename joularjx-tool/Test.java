public class Test {
    public static void main(String[] args) throws InterruptedException {
        long sum = 0;
        for (long i = 0; i < 5_000_000_000L; i++) {
            sum += i;
        }
        System.out.println("Resultado: " + sum);
    }
}

