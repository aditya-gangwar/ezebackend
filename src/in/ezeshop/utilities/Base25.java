package in.ezeshop.utilities;

/**
 * Created by adgangwa on 08-04-2016.
 */
public class Base25 {
    // 'capital O' used as filler
    private static final String ALPHABET = "ABCDEFGHIJKLMNPQRSTUVWXYZ";

    private static final int BASE = ALPHABET.length();

    private Base25() {}

    public static String fromBase10(long i,  int idLen) {
        StringBuilder sb = new StringBuilder("");
        while (i > 0) {
            i = fromBase10(i, sb);
        }
        //String base61 =  sb.reverse().toString();
        sb.reverse();

        // add O as filler
        for (int toAppend = (idLen-sb.length()); toAppend>0; toAppend--) {
            sb.append('O');
        }
        return sb.toString();
    }

    private static long fromBase10(long i, final StringBuilder sb) {
        int rem = (int)(i % BASE);
        sb.append(ALPHABET.charAt(rem));
        return i / BASE;
    }

    public static int toBase10(String str) {
        return toBase10(new StringBuilder(str).reverse().toString().toCharArray());
    }

    private static int toBase10(char[] chars) {
        int n = 0;
        for (int i = chars.length - 1; i >= 0; i--) {
            n += toBase10(ALPHABET.indexOf(chars[i]), i);
        }
        return n;
    }

    private static int toBase10(int n, int pow) {
        return n * (int) Math.pow(BASE, pow);
    }
}
